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

    /**
     * Konstruerer en [FieldValueList]-rad med primitive verdier, slik BigQuery SDK returnerer dem.
     * Brukes til å simulere rader fra historikktabellen i tester.
     */
    private fun mockRad(vararg verdier: Any): FieldValueList =
        FieldValueList.of(
            verdier.map { FieldValue.of(FieldValue.Attribute.PRIMITIVE, it.toString()) }
        )
}
