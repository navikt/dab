package no.nav.poao.dab.bigquery.migrator

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.TableId
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.zip.Adler32

/**
 * Kjører versjonerte SQL-migrasjoner mot BigQuery ved applikasjonsoppstart,
 * på tilsvarende måte som Flyway gjør for relasjonsdatabaser.
 *
 * ## Motivasjon
 *
 * Uten versjonskontroll av BigQuery-tabeller er det lett å miste oversikten over hva som
 * er opprettet, av hvem og når. `BigQueryMigrator` løser dette ved å:
 * - Lagre migrasjonshistorikk i en dedikert tabell ([historyTable]) i samme dataset.
 * - Validere sjekksummer slik at ingen kjørt migrasjon kan endres i ettertid.
 * - Feile hardt ved oppstart dersom en migrasjon ikke har gått gjennom – applikasjonen
 *   starter ikke med inkonsistent skjema.
 *
 * ## Filnavn-konvensjon
 *
 * Identisk med Flyway: `V{heltall}__{beskrivelse}.sql`
 *
 * ```
 * src/main/resources/
 *   db/bigquery/
 *     V1__mine_hendelser.sql
 *     V2__legg_til_kolonne.sql
 * ```
 *
 * Versjonsnummeret er et heltall (ikke semantisk versjonering). `V10` sorteres alltid
 * etter `V9`, aldri etter `V1` (numerisk sortering, ikke leksikografisk).
 *
 * ## SQL-innhold
 *
 * Hver migrasjonsfil bør kun inneholde idempotent DDL:
 * ```sql
 * CREATE TABLE IF NOT EXISTS mitt_dataset.mine_hendelser (
 *     id        STRING    NOT NULL,
 *     timestamp TIMESTAMP NOT NULL
 * );
 *
 * -- Flere statements per fil er støttet – adskilt av semikolon etterfulgt av linjeskift:
 * CREATE TABLE IF NOT EXISTS mitt_dataset.andre_hendelser (
 *     id STRING NOT NULL
 * )
 * ```
 *
 * ## Spring-oppsett
 *
 * `BigQueryMigrator` er en vanlig klasse uten Spring-avhengigheter. Anbefalt oppsett i
 * en `@Configuration`-klasse:
 *
 * ```kotlin
 * @Profile("!test")
 * @Configuration
 * open class BigQueryConfig(@Value("\${app.gcp.projectId}") val projectId: String) {
 *
 *     @Bean
 *     open fun bigQuery(): BigQuery =
 *         BigQueryOptions.newBuilder().setProjectId(projectId).build().service
 *
 *     // Migrasjoner kjøres én gang ved oppstart, garantert før andre BQ-beans:
 *     @Bean
 *     open fun bigQueryMigrator(bigQuery: BigQuery): BigQueryMigrator =
 *         BigQueryMigrator(bigQuery, dataset = "mitt_dataset").also { it.migrate() }
 *
 *     @Bean
 *     @DependsOn("bigQueryMigrator")
 *     open fun bigQueryClient(bigQuery: BigQuery): BigQueryClient =
 *         BigQueryClientImplementation(bigQuery)
 * }
 * ```
 *
 * ## Feilhåndtering
 *
 * | Situasjon | Resultat |
 * |---|---|
 * | Sjekksum-avvik på kjørt migrasjon | `IllegalStateException` – applikasjonen starter ikke |
 * | Feilet migrasjon i historikk (`success = false`) | `IllegalStateException` – applikasjonen starter ikke |
 * | SQL-feil under kjøring | Logger feil, skriver `success = false` i historikk, kaster videre |
 *
 * @param bigQuery BigQuery-klient. Bruker Workload Identity (NAIS) om ikke annet er konfigurert.
 * @param dataset Navn på BigQuery-datasett, f.eks. `"mitt_dataset"`.
 * @param migrationLocation Classpath-sti til mappen med SQL-filer. Standard: `"db/bigquery"`.
 */
class BigQueryMigrator(
    private val bigQuery: BigQuery,
    private val dataset: String,
    private val migrationLocation: String = "db/bigquery",
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val historyTable = "bq_schema_history"

    /**
     * Kjører alle ventende migrasjoner mot [dataset] i rekkefølge.
     *
     * Kallet er idempotent: allerede kjørte migrasjoner hoppes over. Metoden er ment å
     * kalles én gang ved applikasjonsoppstart.
     *
     * @throws IllegalStateException dersom historikken inneholder en feilet migrasjon,
     *   eller dersom en kjørt migrasjons sjekksum ikke stemmer overens med filen på classpath.
     */
    fun migrate() {
        log.info("Starter BigQuery-migrasjoner mot dataset '$dataset'")
        ensureHistoryTableExists()

        val applied = fetchAppliedMigrations()
        val pending = findMigrationFiles().filter { it.version !in applied.map { a -> a.version } }

        if (pending.isEmpty()) {
            log.info("Ingen ventende BigQuery-migrasjoner")
            return
        }

        validateChecksums(findMigrationFiles(), applied)

        pending.forEach { migration ->
            log.info("Kjører BigQuery-migrasjon: ${migration.script}")
            runCatching { execute(migration) }
                .onSuccess { recordHistory(migration, success = true) }
                .onFailure { ex ->
                    recordHistory(migration, success = false)
                    throw IllegalStateException("BigQuery-migrasjon ${migration.script} feilet", ex)
                }
        }
        log.info("${pending.size} BigQuery-migrasjoner kjørt")
    }

    private fun ensureHistoryTableExists() {
        val sql = """
            CREATE TABLE IF NOT EXISTS $dataset.$historyTable (
                version      STRING    NOT NULL,
                description  STRING    NOT NULL,
                script       STRING    NOT NULL,
                checksum     INT64     NOT NULL,
                installed_on TIMESTAMP NOT NULL,
                success      BOOL      NOT NULL
            )
        """.trimIndent()
        bigQuery.query(QueryJobConfiguration.of(sql))
    }

    private fun fetchAppliedMigrations(): List<AppliedMigration> {
        val failedMigrations = bigQuery.query(
            QueryJobConfiguration.of("SELECT script FROM $dataset.$historyTable WHERE success = FALSE")
        ).iterateAll().map { it[0].stringValue }

        if (failedMigrations.isNotEmpty()) {
            throw IllegalStateException(
                "BigQuery har feilede migrasjoner som må rettes manuelt: $failedMigrations"
            )
        }

        return bigQuery.query(
            QueryJobConfiguration.of("SELECT version, checksum FROM $dataset.$historyTable WHERE success = TRUE")
        ).iterateAll().map {
            AppliedMigration(version = it[0].longValue, checksum = it[1].longValue)
        }
    }

    /**
     * Laster og parser alle gyldige migrasjonsfiler fra [migrationLocation] på classpath,
     * sortert etter versjonsnummer (numerisk, ikke leksikografisk).
     *
     * Filer som ikke matcher mønsteret `V{heltall}__{beskrivelse}.sql` ignoreres stille.
     */
    internal fun findMigrationFiles(): List<Migration> {
        val classLoader = Thread.currentThread().contextClassLoader
        val resourceDir = classLoader.getResource(migrationLocation)
            ?: run {
                log.warn("Fant ingen migrasjonsmappe på classpath: $migrationLocation")
                return emptyList()
            }

        return java.io.File(resourceDir.toURI())
            .listFiles { f -> f.name.matches(Regex("V\\d+__.+\\.sql")) }
            .orEmpty()
            .map { file ->
                val (version, description) = parseFileName(file.name)
                val sql = file.readText()
                Migration(
                    version = version,
                    description = description,
                    script = file.name,
                    sql = sql,
                    checksum = checksum(sql),
                )
            }
            .sortedBy { it.version }
    }

    /**
     * Parser versjonsnummer og beskrivelse ut av et Flyway-kompatibelt filnavn.
     *
     * Eksempler:
     * - `"V1__opprett_tabell.sql"` → `(1L, "opprett tabell")`
     * - `"V10__legg_til_kolonne.sql"` → `(10L, "legg til kolonne")`
     *
     * @throws IllegalArgumentException dersom filnavnet ikke matcher mønsteret `V{heltall}__{beskrivelse}.sql`.
     */
    internal fun parseFileName(filename: String): Pair<Long, String> {
        val match = Regex("V(\\d+)__(.+)\\.sql").matchEntire(filename)
            ?: throw IllegalArgumentException("Ugyldig migrasjonsfilnavn: $filename. Forventet format: V{heltall}__{beskrivelse}.sql")
        val version = match.groupValues[1].toLong()
        val description = match.groupValues[2].replace('_', ' ')
        return version to description
    }

    /**
     * Beregner Adler32-sjekksum av SQL-innholdet.
     *
     * Sjekksummen brukes til å oppdage om en allerede kjørt migrasjonsfil er endret.
     * Adler32 er valgt for sin enkelhet og tilstrekkelige kollisjonsmotstand for dette formålet.
     */
    internal fun checksum(sql: String): Long {
        val adler = Adler32()
        adler.update(sql.toByteArray(Charsets.UTF_8))
        return adler.value
    }

    private fun validateChecksums(allFiles: List<Migration>, applied: List<AppliedMigration>) {
        val appliedByVersion = applied.associateBy { it.version }
        allFiles.forEach { migration ->
            val appliedMigration = appliedByVersion[migration.version] ?: return@forEach
            if (appliedMigration.checksum != migration.checksum) {
                throw IllegalStateException(
                    "Sjekksum-avvik for BigQuery-migrasjon '${migration.script}'. " +
                    "Migrasjonen er allerede kjørt og kan ikke endres. " +
                    "Forventet sjekksum: ${appliedMigration.checksum}, faktisk: ${migration.checksum}"
                )
            }
        }
    }

    private fun execute(migration: Migration) {
        migration.sql
            .split(Regex(";\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { statement ->
                bigQuery.query(QueryJobConfiguration.of(statement))
            }
    }

    private fun recordHistory(migration: Migration, success: Boolean) {
        val row = mapOf(
            "version" to migration.version.toString(),
            "description" to migration.description,
            "script" to migration.script,
            "checksum" to migration.checksum,
            "installed_on" to Instant.now().toString(),
            "success" to success,
        )
        bigQuery.insertAll(
            InsertAllRequest.newBuilder(TableId.of(dataset, historyTable))
                .addRow(row)
                .build()
        )
    }

    /** Representerer en migrasjonsfil lastet fra classpath. */
    data class Migration(
        val version: Long,
        val description: String,
        val script: String,
        val sql: String,
        val checksum: Long,
    )

    /** Representerer en migrasjon som allerede er kjørt ifølge [historyTable]. */
    data class AppliedMigration(val version: Long, val checksum: Long)
}
