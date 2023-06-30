package no.nav.poao.dab.spring_auth

import org.slf4j.LoggerFactory
import no.nav.common.auth.context.AuthContextHolder
import no.nav.common.types.identer.*
import no.nav.poao.dab.spring_auth.EksternBrukerAuth.sjekkEksternBrukerHarTilgang
import no.nav.poao.dab.spring_auth.SystemAuth.sjekkErSystemkallFraAzureAd
import no.nav.poao_tilgang.client.NavAnsattTilgangTilNavEnhetPolicyInput
import no.nav.poao_tilgang.client.PoaoTilgangClient
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*


@Service
class AuthService(
    private val authContextHolder: AuthContextHolder,
    private val poaoTilgangClient: PoaoTilgangClient,
    private val personService: IPersonService,
) : IAuthService {
    private val log = LoggerFactory.getLogger(javaClass)
    private val internBrukerAuth: InternBrukerAuth = InternBrukerAuth(poaoTilgangClient)

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

    override fun sjekkTilgangTilPerson(ident: EksternBrukerId) {
        val principal = principal()
        when (principal) {
            is EksternBrukerPrincipal -> sjekkEksternBrukerHarTilgang(principal, ident.toFnr())
            is SystemPrincipal -> sjekkErSystemkallFraAzureAd(authContextHolder.requireIdTokenClaims(), authContextHolder.role.get())
            is VeilederPrincipal -> internBrukerAuth.sjekkInternbrukerHarLeseTilgangTilPerson(requereInternbrukerOid(), ident.toFnr())
        }
    }

    override fun harTilgangTilEnhet(enhet: EnhetId): Boolean {
        return when {
            erEksternBruker() -> return true
            else -> poaoTilgangClient.evaluatePolicy(NavAnsattTilgangTilNavEnhetPolicyInput(requereInternbrukerOid(), enhet.get())).get()?.isPermit ?: false
        }
    }
    override fun sjekkTilgangTilEnhet(enhet: EnhetId) {
        when {
            erEksternBruker() -> return
            else -> if (!harTilgangTilEnhet(enhet))
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Veileder har ikke tilgang til kontor-enhet")
        }
    }

    override fun sjekkInternbrukerHarSkriveTilgangTilPerson(aktorId: AktorId) {
        val navIdent = getInnloggetVeilederIdent()
        internBrukerAuth.sjekkInternbrukerHarSkriveTilgangTilPerson(requereInternbrukerOid(), aktorId)
    }

    private fun requereInternbrukerOid(): UUID {
        if(!erInternBruker()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bruker er ikke internbruker")
        }
        return authContextHolder.requireOid()
    }

    override fun getInnloggetVeilederIdent(): NavIdent {
        val principal = principal()
        return when (principal) {
            is VeilederPrincipal -> principal.navIdent()
            else -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bruker er ikke veileder")
        }
    }
    override fun getLoggedInnUser(): Id {
        return principal().let {
            when (it) {
                is VeilederPrincipal -> it.navIdent()
                is EksternBrukerPrincipal -> it.brukerIdent()
                else -> NavIdent.of(it.jwtClaimsSet.subject) // Ex: "dev-fss:pto:veilarbdialog"
            }
        }
    }

    override fun sjekkAtApplikasjonErIAllowList(allowlist: Array<String>) = sjekkAtApplikasjonErIAllowList(allowlist.asList())
    override fun sjekkAtApplikasjonErIAllowList(allowlist: List<String?>) {
        val appname = principal().getFullAppName()
        if (allowlist.isNotEmpty() && allowlist.contains(appname)) {
            return
        }
        log.error("Applikasjon {} er ikke allowlist", appname)
        throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }

    override fun erEksternBruker() = authContextHolder.erEksternBruker()
    override fun erInternBruker() = authContextHolder.erInternBruker()
    override fun erSystemBruker() = authContextHolder.erSystemBruker()
    override fun erLoggetInn() = !authContextHolder.idTokenString.isEmpty

    override fun erSystemBrukerFraAzureAd(): Boolean = principal() is SystemPrincipal

    private val aktorIdForEksternBruker: Optional<AktorId>
        get() {
            return when {
                authContextHolder.erEksternBruker() -> authContextHolder.uid
                    .map { sub -> personService.getAktorIdForPersonBruker(Fnr.of(sub)) }
                else -> Optional.empty()
            }
        }

    override fun getInnloggetBrukerToken(): String {
        return authContextHolder.requireIdTokenString();
    }
}
