package no.nav.poao.dab.bigquery.migrator

import com.google.cloud.bigquery.BigQuery
import com.google.cloud.bigquery.FieldValue
import com.google.cloud.bigquery.FieldValueList
import com.google.cloud.bigquery.InsertAllRequest
import com.google.cloud.bigquery.InsertAllResponse
import com.google.cloud.bigquery.QueryJobConfiguration
import com.google.cloud.bigquery.TableResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BigQueryMigratorTest {

    private val bigQuery = mockk<BigQuery>()
    private val emptyTableResult = mockk<TableResult>()
    private val emptyInsertResponse = mockk<InsertAllResponse>()

    private val migrator = BigQueryMigrator(
        bigQuery = bigQuery,
        dataset = "test_dataset",
        migrationLocation = "db/bigquery",
    )

    @BeforeEach
    fun setup() {
        every { emptyTableResult.iterateAll() } returns emptyList()
        every { emptyInsertResponse.insertErrors } returns emptyMap()
        every { bigQuery.query(any<QueryJobConfiguration>()) } returns emptyTableResult
        every { bigQuery.insertAll(any<InsertAllRequest>()) } returns emptyInsertResponse
    }

    // --- parseFileName ---

    @Test
    fun `parser versjon og beskrivelse fra filnavn`() {
        val (versjon, beskrivelse) = migrator.parseFileName("V3__opprett_statistikk.sql")
        assertThat(versjon).isEqualTo(3L)
        assertThat(beskrivelse).isEqualTo("opprett statistikk")
    }

    @Test
    fun `parser filnavn med underscores i beskrivelse`() {
        val (versjon, beskrivelse) = migrator.parseFileName("V1__opprett_hendelser.sql")
        assertThat(versjon).isEqualTo(1L)
        assertThat(beskrivelse).isEqualTo("opprett hendelser")
    }

    @Test
    fun `feiler på ugyldig filnavn`() {
        assertThatThrownBy { migrator.parseFileName("ugyldig.sql") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ugyldig.sql")
    }

    // --- checksum ---

    @Test
    fun `beregner stabil sjekksum for samme innhold`() {
        val sql = "CREATE TABLE IF NOT EXISTS test (id STRING)"
        assertThat(migrator.checksum(sql)).isEqualTo(migrator.checksum(sql))
    }

    @Test
    fun `sjekksum er ulik for ulikt innhold`() {
        val sql1 = "CREATE TABLE IF NOT EXISTS test (id STRING)"
        val sql2 = "CREATE TABLE IF NOT EXISTS test (id STRING, navn STRING)"
        assertThat(migrator.checksum(sql1)).isNotEqualTo(migrator.checksum(sql2))
    }

    // --- findMigrationFiles ---

    @Test
    fun `sorterer migrasjoner etter versjonsnummer, ikke leksikografisk`() {
        val versjoner = migrator.findMigrationFiles().map { it.version }
        // V1 og V2 finnes i src/test/resources/db/bigquery/
        assertThat(versjoner).isSortedAccordingTo(compareBy { it })
        assertThat(versjoner).contains(1L, 2L)
    }

    // --- migrate ---

    @Test
    fun `hopper over allerede kjørte migrasjoner`() {
        val migrasjoner = migrator.findMigrationFiles()

        val historikkResultat = mockk<TableResult>()
        every { historikkResultat.iterateAll() } returns
            migrasjoner.map { mockRad(it.version.toString(), it.checksum) }

        // Sekvens: CREATE history → SELECT failed → SELECT applied (alle kjørt)
        every { bigQuery.query(any<QueryJobConfiguration>()) } returnsMany listOf(
            emptyTableResult,
            emptyTableResult,
            historikkResultat,
        )

        migrator.migrate()

        // Kun de 3 innledende spørringene – ingen migrasjoner kjøres på nytt
        verify(exactly = 3) { bigQuery.query(any<QueryJobConfiguration>()) }
        verify(exactly = 0) { bigQuery.insertAll(any<InsertAllRequest>()) }
    }

    @Test
    fun `skriver historikk med suksess=true etter vellykket migrasjon`() {
        every { bigQuery.query(any<QueryJobConfiguration>()) } returnsMany listOf(
            emptyTableResult, // CREATE history table
            emptyTableResult, // SELECT failed
            emptyTableResult, // SELECT applied (ingen kjørt)
        ) andThenAnswer { emptyTableResult }

        migrator.migrate()

        val antallMigrasjoner = migrator.findMigrationFiles().size
        val insertRequests = mutableListOf<InsertAllRequest>()
        verify(exactly = antallMigrasjoner) {
            bigQuery.insertAll(capture(insertRequests))
        }

        insertRequests.forEach { request ->
            val row = request.rows.first().content
            assertThat(row["success"]).isEqualTo(true)
        }
    }

    @Test
    fun `feiler hardt ved sjekksum-avvik`() {
        val forsteMigrasjon = migrator.findMigrationFiles().first()

        val historikkResultat = mockk<TableResult>()
        every { historikkResultat.iterateAll() } returns
            listOf(mockRad(forsteMigrasjon.version.toString(), forsteMigrasjon.checksum + 1))

        every { bigQuery.query(any<QueryJobConfiguration>()) } returnsMany listOf(
            emptyTableResult,
            emptyTableResult,
            historikkResultat,
        )

        assertThatThrownBy { migrator.migrate() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Sjekksum-avvik")
            .hasMessageContaining(forsteMigrasjon.script)
    }

    @Test
    fun `feiler hardt ved feilet migrasjon i historikk`() {
        val feiletMigrasjon = migrator.findMigrationFiles().first()

        val feiletResultat = mockk<TableResult>()
        every { feiletResultat.iterateAll() } returns
            listOf(mockRad(feiletMigrasjon.script, 0))

        every { bigQuery.query(any<QueryJobConfiguration>()) } returnsMany listOf(
            emptyTableResult, // CREATE history table
            feiletResultat,  // SELECT failed → returnerer feilet migrasjon
        )

        assertThatThrownBy { migrator.migrate() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("feilede migrasjoner")
    }

    // --- scanJarEntries ---

    @Test
    fun `finner migrasjoner i JAR-fil via scanJarEntries`() {
        val tempJar = lagMidlertidigJar(mapOf(
            "BOOT-INF/classes/db/bigquery/V1__opprett_hendelser.sql" to "CREATE TABLE hendelser (id STRING)",
            "BOOT-INF/classes/db/bigquery/V3__legg_til_kolonne.sql" to "ALTER TABLE hendelser ADD COLUMN navn STRING",
            "BOOT-INF/classes/db/bigquery/V2__opprett_statistikk.sql" to "CREATE TABLE statistikk (id STRING)",
            "BOOT-INF/classes/db/bigquery/ikke_en_migrasjon.txt" to "ignorert",
            "annen/sti/V99__skal_ignoreres.sql" to "CREATE TABLE annet (id STRING)",
        ))
        try {
            val result = migrator.scanJarEntries(
                jarFilePath = tempJar.absolutePath,
                entryPrefix = "BOOT-INF/classes/db/bigquery/",
            )
            assertThat(result.map { it.version }).containsExactlyInAnyOrder(1L, 2L, 3L)
            assertThat(result.map { it.script }).allMatch { it.matches(Regex("V\\d+__.+\\.sql")) }
        } finally {
            tempJar.delete()
        }
    }

    @Test
    fun `scanJarEntries ignorerer filer i underkataloger`() {
        val tempJar = lagMidlertidigJar(mapOf(
            "db/bigquery/V1__rot.sql" to "CREATE TABLE rot (id STRING)",
            "db/bigquery/underkatalog/V2__skal_ignoreres.sql" to "CREATE TABLE underkatalog (id STRING)",
        ))
        try {
            val result = migrator.scanJarEntries(
                jarFilePath = tempJar.absolutePath,
                entryPrefix = "db/bigquery/",
            )
            assertThat(result.map { it.version }).containsExactly(1L)
        } finally {
            tempJar.delete()
        }
    }

    // --- URL-format: nested: (Spring Boot 3.2+) ---

    @Test
    fun `finner migrasjoner med nested JAR-URL format (Spring Boot 4_x jar-nested)`() {
        val tempJar = lagMidlertidigJar(mapOf(
            "BOOT-INF/classes/db/bigquery/V1__opprett_tabell.sql" to "CREATE TABLE t (id STRING)",
            "BOOT-INF/classes/db/bigquery/V2__legg_til_kolonne.sql" to "ALTER TABLE t ADD COLUMN navn STRING",
        ))
        try {
            // Verifiserer URL-parsingen direkte med den faktisk observerte URL-stien fra Spring Boot 4.x:
            // protocol=jar, path=nested:/app/app.jar/!BOOT-INF/classes/!/db/bigquery
            // Merk: trailing slash på "classes/" FØR "!" – dette gir dobbel skråstrek uten riktig parsing
            val simulertPath = "nested:${tempJar.absolutePath}/!BOOT-INF/classes/!/db/bigquery"
            val rawPath = simulertPath.removePrefix("nested:")
            val parts = rawPath.split("/!")
            val jarFilePath = parts[0].trimEnd('/')
            val entryPrefix = parts.drop(1)
                .map { it.trim('/') }
                .filter { it.isNotEmpty() }
                .joinToString("/") + "/"

            assertThat(jarFilePath).isEqualTo(tempJar.absolutePath)
            assertThat(entryPrefix).isEqualTo("BOOT-INF/classes/db/bigquery/")

            val result = migrator.scanJarEntries(jarFilePath, entryPrefix)
            assertThat(result.map { it.version }).containsExactlyInAnyOrder(1L, 2L)
        } finally {
            tempJar.delete()
        }
    }

    /**
     * Konstruerer en [FieldValueList]-rad med primitive verdier, slik BigQuery SDK returnerer dem.
     * Brukes til å simulere rader fra historikktabellen i tester.
     */
    private fun mockRad(vararg verdier: Any): FieldValueList =
        FieldValueList.of(
            verdier.map { FieldValue.of(FieldValue.Attribute.PRIMITIVE, it.toString()) }
        )

    private fun lagMidlertidigJar(entries: Map<String, String>): java.io.File {
        val tempFile = java.io.File.createTempFile("test-migrasjoner", ".jar")
        java.util.jar.JarOutputStream(tempFile.outputStream()).use { jar ->
            entries.forEach { (name, innhold) ->
                jar.putNextEntry(java.util.jar.JarEntry(name))
                jar.write(innhold.toByteArray(Charsets.UTF_8))
                jar.closeEntry()
            }
        }
        return tempFile
    }
}
