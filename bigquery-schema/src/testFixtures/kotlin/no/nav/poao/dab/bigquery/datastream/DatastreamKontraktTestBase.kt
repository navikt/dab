package no.nav.poao.dab.bigquery.datastream

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Abstrakt basisklasse for tester som verifiserer at en Datastream-kontrakt stemmer overens
 * med det faktiske databaseskjemaet i Postgres.
 *
 * ## Hva som testes
 *
 * Klassen inneholder tre tester som kjøres mot en innebygd eller ekstern Postgres-instans:
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
 * ## Bruk
 *
 * Legg til avhengigheten i `build.gradle.kts`:
 * ```kotlin
 * testImplementation(testFixtures("no.nav.poao.dab:bigquery-schema:<versjon>"))
 * ```
 *
 * Opprett en testklasse som extender denne:
 * ```kotlin
 * class DatastreamSkjemaTest : DatastreamKontraktTestBase() {
 *
 *     // Angi hvilke tabeller som skal testes:
 *     override val tabeller = DatastreamKontrakt.tabeller
 *
 *     // Gi tilgang til en JdbcTemplate koblet mot den innebygde Postgres-instansen:
 *     override val jdbcTemplate = LocalDatabaseSingleton.jdbcTemplate
 * }
 * ```
 *
 * De tre testene fra denne klassen arves automatisk og kjøres som en del av testsuiten.
 *
 * @see Tabell
 */
abstract class DatastreamKontraktTestBase {

    /** Liste over tabellene som skal verifiseres mot databaseskjema. */
    abstract val tabeller: List<Tabell>

    /** JdbcTemplate koblet mot en Postgres-instans der skjemaet er initialisert (f.eks. embedded Postgres). */
    abstract val jdbcTemplate: JdbcTemplate

    @Test
    fun `alle datastream-tabeller eksisterer i databaseskjema`() {
        val eksisterendeTabeller = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String::class.java
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
            val eksisterendeKolonner = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String::class.java,
                tabell.navn
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
            val alleKolonnerIDb = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?",
                String::class.java,
                tabell.navn
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
}
