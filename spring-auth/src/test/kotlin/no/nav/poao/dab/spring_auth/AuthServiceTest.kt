package no.nav.poao.dab.spring_auth

import com.nimbusds.jwt.JWTClaimsSet
import io.mockk.every
import no.nav.common.auth.context.AuthContextHolder
import io.mockk.mockk
import io.mockk.verify
import no.nav.common.auth.Constants
import no.nav.common.auth.context.UserRole
import no.nav.common.types.identer.Fnr
import no.nav.common.types.identer.NavIdent
import no.nav.poao_tilgang.client.*
import no.nav.poao_tilgang.client.api.ApiResult
import org.junit.jupiter.api.Test
import java.util.*

class AuthServiceTest {

    val authContextHolder: AuthContextHolder = mockk()
    val poaoTilgangClient: PoaoTilgangClient = mockk()
    val personService: IPersonService = mockk()
    val applicationName: String = "testApplication"

    val authService = AuthService(
        authContextHolder, poaoTilgangClient, personService, applicationName
    )

    data class MockSetup(
        val navAnsattIdent: NavIdent,
        val navAnsattOid: UUID,
        val brukerIdent: Fnr
    )

    fun setupMock(navIdent: String, brukerIdent: String): MockSetup {
        val navAnsattIdent = NavIdent(navIdent)
        val navAnsattOid = UUID.randomUUID()
        val brukerIdent = Fnr(brukerIdent)
        val innloggedClaims = JWTClaimsSet.Builder()
            .claim(Constants.AAD_NAV_IDENT_CLAIM, navAnsattIdent).build()
        every { authContextHolder.requireIdTokenClaims() } returns innloggedClaims
        every { authContextHolder.role } returns Optional.of(UserRole.INTERN)
        every { authContextHolder.erInternBruker() } returns true
        every { authContextHolder.requireOid() } returns navAnsattOid
        every { authContextHolder.navIdent } returns Optional.of(navAnsattIdent)
        every { personService.getFnrForAktorId(any()) } returns brukerIdent
        every { poaoTilgangClient.evaluatePolicy(any()) } returns ApiResult.success(Decision.Permit)
        return MockSetup(navAnsattIdent, navAnsattOid, brukerIdent)
    }

    @Test
    fun `skal skrivetilgang hvis harTilgangTilPerson er kalt med skrivetilgang`() {
        val (_, navAnsattOid, brukerIdent) = setupMock("navIdent", "brukerIdent")
        authService.harTilgangTilPerson(
            brukerIdent,
            TilgangsType.SKRIVE
        )
        val policy = NavAnsattTilgangTilEksternBrukerPolicyInput(
            navAnsattOid,
            TilgangType.SKRIVE,
            brukerIdent.get()
        )
        verify { poaoTilgangClient.evaluatePolicy(policy) }
    }

    @Test
    fun `skal lesetilgang hvis harTilgangTilPerson er kalt med lesetilgang`() {
        val (_, navAnsattOid, brukerIdent) = setupMock("navIdent", "brukerIdent")
        authService.harTilgangTilPerson(
            brukerIdent,
            TilgangsType.LESE
        )
        val policy = NavAnsattTilgangTilEksternBrukerPolicyInput(
            navAnsattOid,
            TilgangType.LESE,
            brukerIdent.get()
        )
        verify { poaoTilgangClient.evaluatePolicy(policy) }
    }

}