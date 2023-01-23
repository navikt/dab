package no.nav.poao.dab.spring_auth

import no.nav.common.abac.Pep
import no.nav.common.auth.context.AuthContextHolder
import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.EksternBrukerId
import no.nav.common.types.identer.EnhetId
import no.nav.common.types.identer.Fnr
import no.nav.common.types.identer.Id
import no.nav.poao.dab.spring_auth.SystemAuth.sjekkErSystemkallFraAzureAd
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

import no.nav.common.types.identer.NavIdent

@Service
class AuthService(
    private val authContextHolder: AuthContextHolder,
    private val veilarbPep: Pep,
    private val personService: PersonService,
) {
    private val internBrukerAuth: InternBrukerAuth = InternBrukerAuth(veilarbPep)
    private val eksternBrukerAuth: EksternBrukerAuth = EksternBrukerAuth(personService)

    private fun principal(): NavPrincipal {
        return NavPrincipal.of(
            authContextHolder.requireIdTokenClaims(),
            authContextHolder.role.get()
        )
    }

    fun sjekkTilgangTilPerson(ident: EksternBrukerId) {
        val principal = principal()
        when (principal) {
            is EksternBrukerPrincipal -> eksternBrukerAuth.sjekkEksternBrukerHarTilgang(principal, ident)
            is SystemPrincipal -> sjekkErSystemkallFraAzureAd(authContextHolder.requireIdTokenClaims(), authContextHolder.role.get())
            is VeilederPrincipal -> sjekkInternBrukerHarTilgang(ident)
        }
    }

    private fun sjekkInternBrukerHarTilgang(eksternBrukerId: EksternBrukerId) {
        val aktorId = personService.getAktorIdForPersonBruker(eksternBrukerId)
        val principal = principal()
        when (principal) {
            is VeilederPrincipal -> internBrukerAuth.sjekkInternbrukerHarLeseTilgangTilPerson(principal.navIdent(), aktorId)
            else -> IllegalStateException("Fant ikke token til innlogget bruker")
        }
    }

    fun sjekKvpTilgang(enhet: EnhetId): Boolean {
        return when {
            erEksternBruker() -> true
            else -> veilarbPep.harVeilederTilgangTilEnhet(innloggetVeilederIdent, enhet)
        }
    }

    fun sjekkInternbrukerHarSkriveTilgangTilPerson(aktorId: AktorId) {
        val navIdent = innloggetVeilederIdent
        internBrukerAuth.sjekkInternbrukerHarSkriveTilgangTilPerson(navIdent, aktorId)
    }

    val innloggetVeilederIdent: NavIdent
        get() {
            val principal = principal()
            return when (principal) {
                is VeilederPrincipal -> principal.navIdent()
                else -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bruker er ikke veileder")
            }
        }
    val loggedInnUser: Id
        get() = principal().let {
            when (it) {
                is VeilederPrincipal -> it.navIdent()
                is EksternBrukerPrincipal -> it.brukerIdent()
                else -> NavIdent.of(it.jwtClaimsSet.subject)
            }
        }
    val innloggetBrukerIdent: Optional<String>
        get() = authContextHolder.uid

    fun erEksternBruker() = authContextHolder.erEksternBruker()
    fun erInternBruker() = authContextHolder.erInternBruker()
    fun erSystemBruker() = authContextHolder.erSystemBruker()

    private val aktorIdForEksternBruker: Optional<AktorId>
        get() {
            return when {
                authContextHolder.erEksternBruker() -> authContextHolder.uid
                    .map { sub -> personService.getAktorIdForPersonBruker(Fnr.of(sub)) }
                else -> Optional.empty()
            }
        }
}