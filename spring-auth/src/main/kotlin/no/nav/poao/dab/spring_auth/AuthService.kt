package no.nav.poao.dab.spring_auth

import no.nav.common.abac.Pep
import no.nav.common.abac.domain.request.ActionId
import no.nav.common.auth.context.AuthContextHolder
import no.nav.common.auth.context.UserRole
import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.EksternBrukerId
import no.nav.common.types.identer.EnhetId
import no.nav.common.types.identer.Fnr
import no.nav.common.types.identer.Id
import no.nav.poao.dab.spring_auth.SystemAuth.sjekkErSystemkallFraAzureAd
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.text.ParseException
import java.util.*

package no.nav.veilarbaktivitet.person
import no.nav.common.types.identer.NavIdent

@Service
class AuthService(
    private val authContextHolder: AuthContextHolder,
    private val veilarbPep: Pep,
    private val personService: PersonService
) {

    fun sjekkEksternBrukerHarTilgang(ident: Id) {
        val loggedInUserFnr: Optional<String> = innloggetBrukerIdent
        if (!loggedInUserFnr.map { fnr: String -> (fnr == ident.get()) }.orElse(false)) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "ekstern bruker har ikke tilgang til andre brukere enn seg selv"
            )
        }
        if (!eksternBrukerHasNiva4()) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "ekstern bruker har ikke innloggingsnivå 4"
            )
        }
    }

    private fun eksternBrukerHasNiva4(): Boolean {
        return authContextHolder.getIdTokenClaims()
            .map { jwtClaimsSet ->
                try {
                    return@map Objects.equals(jwtClaimsSet.getStringClaim("acr"), "Level4")
                } catch (e: ParseException) {
                    return@map false
                }
            }.orElse(false)
    }

    private fun getFnrForEksternBruker(ident: Id): Fnr {
        return when (ident) {
            is Fnr -> ident
            is AktorId -> personService.getFnrForAktorId(AktorId(ident.get()))
            else ->    throw IllegalArgumentException("Kan ikke hente fnr for NAV-ansatte")
        }
    }

    fun sjekkTilgangTilPerson(ident: EksternBrukerId) {
        val role = authContextHolder.role.get()
        when (role) {
            UserRole.EKSTERN -> sjekkEksternBrukerHarTilgang(getFnrForEksternBruker(ident))
            UserRole.SYSTEM -> sjekkErSystemkallFraAzureAd(authContextHolder.requireIdTokenClaims(), authContextHolder.role.get())
            UserRole.INTERN -> sjekkInternBrukerHarTilgang(ident)
        }
    }

    private fun sjekkInternBrukerHarTilgang(eksternBrukerId: EksternBrukerId) {
        val aktorId = personService
            .getAktorIdForPersonBruker(eksternBrukerId)
        val innloggetBrukerToken: String = authContextHolder
            .idTokenString
            .orElseThrow { IllegalStateException("Fant ikke token til innlogget bruker") }
        if (!veilarbPep.harTilgangTilPerson(innloggetBrukerToken, ActionId.READ, aktorId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
    }

    fun sjekkTilgangOgInternBruker(aktorid: AktorId, enhet: String?) {
        if (!authContextHolder.erInternBruker()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        sjekkTilgang(aktorid, enhet)
    }

    fun sjekkTilgang(aktorid: AktorId, enhet: String?) {
        sjekkTilgangTilPerson(aktorid)
        if (!sjekKvpTilgang(enhet)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
    }

    fun sjekKvpTilgang(enhet: String?): Boolean {
        if (enhet.isNullOrBlank()) {
            return true
        }
        if (authContextHolder.erEksternBruker()) {
            return true
        }
        return veilarbPep.harVeilederTilgangTilEnhet(innloggetVeilederIdent, EnhetId.of(enhet))
    }

    fun sjekkInternbrukerHarSkriveTilgangTilPerson(aktorId: String?) {
        val harTilgang =
            veilarbPep.harVeilederTilgangTilPerson(innloggetVeilederIdent, ActionId.WRITE, AktorId.of(aktorId))
        if (!harTilgang) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
    }

    val innloggetVeilederIdent: NavIdent
        get() {
            if (authContextHolder.requireRole() !== UserRole.INTERN) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bruker er ikke veileder")
            }
            return authContextHolder
                .getNavIdent()
                .orElseThrow {
                    ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Fant ikke ident for innlogget veileder"
                    )
                }
        }
    val loggedInnUser: Optional<Any>
        get() = authContextHolder
            .role
            .flatMap { role ->
                if (UserRole.EKSTERN.equals(role)) {
                    return@flatMap aktorIdForEksternBruker
                }
                if (UserRole.INTERN.equals(role)) {
                    return@flatMap authContextHolder.getNavIdent().map { ident -> NavIdent(ident.get()) }
                }
                if (UserRole.SYSTEM.equals(role)) {
                    return@flatMap authContextHolder.getSubject().map(NavIdent::of)
                }
                Optional.empty()
            }
    val innloggetBrukerIdent: Optional<String>
        get() {
            return authContextHolder.getUid()
        }

    fun erEksternBruker(): Boolean {
        return authContextHolder.erEksternBruker()
    }

    fun erInternBruker(): Boolean {
        return authContextHolder.erInternBruker()
    }

    fun erSystemBruker(): Boolean {
        return authContextHolder.erSystemBruker()
    }

    private val aktorIdForEksternBruker: Optional<AktorId>
        get() {
            return when {
                authContextHolder.erEksternBruker() -> authContextHolder.uid
                    .map { sub -> personService.getAktorIdForPersonBruker(Fnr.of(sub)) }
                else -> Optional.empty()
            }
        }
}