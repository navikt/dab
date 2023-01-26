package no.nav.poao.dab.spring_auth

import org.slf4j.LoggerFactory
import no.nav.common.abac.Pep
import no.nav.common.auth.context.AuthContextHolder
import no.nav.common.types.identer.*
import no.nav.poao.dab.spring_auth.EksternBrukerAuth.sjekkEksternBrukerHarTilgang
import no.nav.poao.dab.spring_auth.SystemAuth.sjekkErSystemkallFraAzureAd
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.jvm.optionals.getOrNull


@Service
class AuthService(
    private val authContextHolder: AuthContextHolder,
    private val veilarbPep: Pep,
    private val personService: PersonService,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val internBrukerAuth: InternBrukerAuth = InternBrukerAuth(veilarbPep)

    private fun principal(): NavPrincipal {
        try {
            return NavPrincipal.of(
                authContextHolder.requireIdTokenClaims(),
                authContextHolder.role.get()
            )
        } catch (exception: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
    }

    private fun EksternBrukerId.toFnr(): Fnr {
        return if (this is Fnr) this else personService.getFnrForAktorId(this)
    }

    fun sjekkTilgangTilPerson(ident: EksternBrukerId) {
        val principal = principal()
        when (principal) {
            is EksternBrukerPrincipal -> sjekkEksternBrukerHarTilgang(principal, ident.toFnr())
            is SystemPrincipal -> sjekkErSystemkallFraAzureAd(authContextHolder.requireIdTokenClaims(), authContextHolder.role.get())
            is VeilederPrincipal -> internBrukerAuth.sjekkInternbrukerHarLeseTilgangTilPerson(principal.navIdent(), ident.toFnr())
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
    val innloggetBrukerIdent: String?
        get() = authContextHolder.uid.getOrNull()

    fun sjekkAtApplikasjonErIAllowList(allowlist: Array<String>) = sjekkAtApplikasjonErIAllowList(allowlist.asList())
    fun sjekkAtApplikasjonErIAllowList(allowlist: List<String?>) {
        val appname = principal().getFullAppName()
        if (allowlist.isNotEmpty() && allowlist.contains(appname)) {
            return
        }
        log.error("Applikasjon {} er ikke allowlist", appname)
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    fun erEksternBruker() = authContextHolder.erEksternBruker()
    fun erInternBruker() = authContextHolder.erInternBruker()
    fun erSystemBruker() = authContextHolder.erSystemBruker()
    fun erSystemBrukerFraAzureAd(): Boolean = principal() is SystemPrincipal

    private val aktorIdForEksternBruker: Optional<AktorId>
        get() {
            return when {
                authContextHolder.erEksternBruker() -> authContextHolder.uid
                    .map { sub -> personService.getAktorIdForPersonBruker(Fnr.of(sub)) }
                else -> Optional.empty()
            }
        }
}
