package no.nav.poao.dab.bigquery.datastream

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.Connection
import javax.sql.DataSource

/**
 * Abstrakt basisklasse for tester som verifiserer at en Datastream-kontrakt stemmer overens
 * med det faktiske databaseskjemaet i Postgres.
 *
 * Klassen er fri for rammeverk-avhengigheter og fungerer med Spring, Ktor eller andre oppsett
 * som kan tilby en `javax.sql.DataSource`.
 *
 * ## Hva som testes
 *
 * 1. **Alle tabeller eksisterer** – verifiserer at tabellene deklarert i kontrakten finnes i DB.
 * 2. **Alle replikerte kolonner eksisterer** – verifiserer at kolonnene i kontrakten finnes på riktig tabell.
 * 3. **Alle kolonner er deklarert** – verifiserer at ingen kolonne i DB er udokumentert;
 *    enhver kolonne må enten ligge i [Tabell.kolonner] (replikeres) eller [Tabell.ikkeReplikerteKolonner]
 *    (replikeres ikke).
 *
 * Den tredje testen er særlig verdifull: den fanger opp nye kolonner som er lagt til i Postgres
 * uten at noen har tatt stilling til om de skal replikeres til BigQuery.
 *
 * ## Bruk (Spring)
 *
 * ```kotlin
 * class DatastreamSkjemaTest : DatastreamKontraktTestBase() {
 *     override val tabeller = DatastreamKontrakt.tabeller
 *     override val dataSource = LocalDatabaseSingleton.postgres // javax.sql.DataSource
 * }
 * ```
 *
 * ## Bruk (Ktor / annet)
 *
 * ```kotlin
 * class DatastreamSkjemaTest : DatastreamKontraktTestBase() {
 *     override val tabeller = DatastreamKontrakt.tabeller
 *     override val dataSource = hikariDataSource // HikariDataSource implementerer DataSource
 * }
 * ```
 *
 * @see Tabell
 */
abstract class DatastreamKontraktTestBase {

    /** Liste over tabellene som skal verifiseres mot databaseskjema. */
    abstract val tabeller: List<Tabell>

    /**
     * DataSource koblet mot en Postgres-instans der skjemaet er initialisert.
     * Bruker standard `javax.sql.DataSource` slik at klassen er uavhengig av rammeverk.
     */
    abstract val dataSource: DataSource

    @Test
    fun `alle datastream-tabeller eksisterer i databaseskjema`() {
        val eksisterendeTabeller = dataSource.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
        )

        tabeller.forEach { tabell ->
            assertThat(eksisterendeTabeller)
                .withFailMessage(
                    "Datastream-tabell '${tabell.navn}' finnes ikke i databaseskjema. " +
                    "Er tabellen slettet eller omdøpt? Oppdater kontrakten og varsle datavarehus-teamet."
                )
                .contains(tabell.navn)
        }
    }

    @Test
    fun `alle replikerte kolonner eksisterer i databaseskjema`() {
        tabeller.forEach { tabell ->
            val eksisterendeKolonner = dataSource.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                tabell.navn,
            )

            tabell.kolonner.forEach { kolonne ->
                assertThat(eksisterendeKolonner)
                    .withFailMessage(
                        "Kolonne '${kolonne.navn}' på Datastream-tabell '${tabell.navn}' finnes ikke i databaseskjema. " +
                        "Er kolonnen slettet eller omdøpt? Oppdater kontrakten og varsle datavarehus-teamet."
                    )
                    .contains(kolonne.navn)
            }
        }
    }

    @Test
    fun `alle kolonner på datastream-tabeller er deklarert i kontrakten`() {
        tabeller.forEach { tabell ->
            val alleKolonnerIDb = dataSource.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                tabell.navn,
            ).toSet()

            val deklarerteKolonner = tabell.kolonner.map { it.navn }.toSet()
            val udeklarerteKolonner = alleKolonnerIDb - deklarerteKolonner - tabell.ikkeReplikerteKolonner

            assertThat(udeklarerteKolonner)
                .withFailMessage(
                    "Tabell '${tabell.navn}' har kolonner som ikke er deklarert i kontrakten: $udeklarerteKolonner. " +
                    "Legg kolonnen til i 'kolonner' (skal replikeres) eller 'ikkeReplikerteKolonner' (skal ikke replikeres), " +
                    "og varsle datavarehus-teamet om nødvendig."
                )
                .isEmpty()
        }
    }

    /**
     * Kjører en parametrisert spørring og returnerer første kolonne som en liste av strenger.
     * Minimalt JDBC-wrapper uten rammeverk-avhengighet.
     */
    private fun DataSource.queryForList(sql: String, vararg params: Any): List<String> =
        connection.use { conn: Connection ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { i, param -> stmt.setObject(i + 1, param) }
                stmt.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
                }
            }
        }
}
