package no.nav.poao.dab.bigquery.datastream

/**
 * Representerer én enkelt kolonne på en Postgres-tabell som er konfigurert for
 * Datastream-replikering til BigQuery.
 *
 * @property navn Kolonnenavnet slik det er definert i Postgres (`snake_case`).
 * @property aksepterteVerdier Dersom kolonnen kun kan inneholde et begrenset sett av verdier
 *   (f.eks. en enum-kolonne), angis disse her. `null` betyr at kolonnen er uten verdi-begrensning.
 *   Snapshot-tester bør verifisere at dette settet stemmer overens med tilhørende enum-klasse,
 *   slik at endringer i enum-verdier fanges opp ved kompilering/test.
 *
 * @see Tabell
 */
data class Kolonne(
    val navn: String,
    val aksepterteVerdier: Set<String>? = null,
)

/**
 * Representerer én Postgres-tabell som er konfigurert for Datastream-replikering til BigQuery,
 * med eksplisitt angivelse av hvilke kolonner som er med og hvilke som er utelatt.
 *
 * Formålet er å gjøre Datastream-konfigurasjonen synlig i kildekoden slik at:
 * - Utviklere som endrer tabellskjema er klar over at datavarehuset er konsument.
 * - Automatiske tester ([DatastreamKontraktTestBase]) fanger opp avvik mellom kontrakt og DB-skjema.
 * - Endringer i domeneverdier (f.eks. nye enum-verdier) oppdages ved kompilering/test.
 *
 * ## Alle kolonner må deklareres
 *
 * Alle kolonner på tabellen **må** være dekket av enten [kolonner] eller [ikkeReplikerteKolonner].
 * [DatastreamKontraktTestBase.`alle kolonner på datastream-tabeller er deklarert i kontrakten`]
 * verifiserer dette. Dersom en ny kolonne legges til i Postgres uten å oppdatere kontrakten,
 * feiler testen med en beskrivende melding.
 *
 * ## Eksempel
 *
 * ```kotlin
 * // I app-repoet, typisk i en fil kalt DatastreamKontrakt.kt:
 * object DatastreamKontrakt {
 *
 *     val tabeller: List<Tabell> = listOf(
 *         manuellStatus,
 *         oppfolgingsperiode,
 *     )
 *
 *     val manuellStatus = Tabell(
 *         navn = "manuell_status",
 *         kolonner = listOf(
 *             Kolonne("aktor_id"),
 *             Kolonne("manuell"),
 *             Kolonne("opprettet_dato"),
 *         ),
 *         // Kolonner som finnes i Postgres men ikke er konfigurert i Datastream-streamen:
 *         ikkeReplikerteKolonner = setOf("veileder_ident", "begrunnelse"),
 *     )
 *
 *     val oppfolgingsperiode = Tabell(
 *         navn = "oppfolgingsperiode",
 *         kolonner = listOf(
 *             Kolonne("aktor_id"),
 *             Kolonne("uuid"),
 *             Kolonne("startdato"),
 *             Kolonne("sluttdato"),
 *             Kolonne(
 *                 navn = "start_begrunnelse",
 *                 // Snapshot av gyldige verdier – testen feiler om enum-klassen endres
 *                 aksepterteVerdier = OppfolgingStartBegrunnelse.entries.map { it.name }.toSet(),
 *             ),
 *         ),
 *         ikkeReplikerteKolonner = setOf("ao_kontor_intern_person_id"),
 *     )
 * }
 * ```
 *
 * @property navn Tabellnavnet slik det er definert i Postgres (`snake_case`).
 * @property kolonner Kolonner som **er** konfigurert for replikering i Datastream-streamen.
 * @property ikkeReplikerteKolonner Kolonner som finnes i Postgres men **ikke** er konfigurert
 *   for replikering. Disse deklareres eksplisitt slik at alle kolonner er regnskapsført og ingen
 *   stille kan begynne å replikeres uten at noen tar stilling til det.
 *
 * @see DatastreamKontraktTestBase
 */
data class Tabell(
    val navn: String,
    val kolonner: List<Kolonne>,
    val ikkeReplikerteKolonner: Set<String> = emptySet(),
)
