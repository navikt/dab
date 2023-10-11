package no.nav.poao.dab.spring_auth

import no.nav.common.audit_log.cef.AuthorizationDecision
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.common.audit_log.log.AuditLogger
import no.nav.common.audit_log.log.AuditLoggerImpl
import no.nav.common.auth.context.AuthContextHolder
import no.nav.common.types.identer.*
import no.nav.poao.dab.spring_auth.EksternBrukerAuth.harEksternBrukerHarTilgang
import no.nav.poao.dab.spring_auth.SystemAuth.erSystemkallFraAzureAd
import no.nav.poao_tilgang.client.NavAnsattTilgangTilNavEnhetPolicyInput
import no.nav.poao_tilgang.client.PoaoTilgangClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*


@Service
class AuthService(
    private val authContextHolder: AuthContextHolder,
    private val poaoTilgangClient: PoaoTilgangClient,
    private val personService: IPersonService,
    private val applicationName: String
) : IAuthService {
    private val log = LoggerFactory.getLogger(javaClass)
    private val internBrukerAuth: InternBrukerAuth = InternBrukerAuth(poaoTilgangClient, personService)

    private val auditLogger: AuditLogger = AuditLoggerImpl()

    fun logIfNotSystemAccess(result: AuthResult, message: String) {
        when (result) {
            is AuthResult.UserFailedResult -> log(false, result.subjectIdent, result.objectIdent, message)
            is AuthResult.UserSuccessResult -> log(true, result.subjectIdent, result.objectIdent, message)
            is AuthResult.UnAuditedFailedResult -> return
            is AuthResult.UnAuditedSuccessResult -> return
        }
    }

    private fun log(harTilgang: Boolean, subjectIdent: Id, objectIdent: Id, message: String) {
        CefMessage.builder()
            .authorizationDecision(if (harTilgang) AuthorizationDecision.PERMIT else AuthorizationDecision.DENY)
            .sourceUserId(subjectIdent.get())
            .destinationUserId(objectIdent.get())
            .applicationName(applicationName)
            .extension("msg", message)
            .event(CefMessageEvent.ACCESS)
            .name("$applicationName Sporingslogg")
            .timeEnded(System.currentTimeMillis()).build()
            .let { auditLogger.log(it) }
    }

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
       harTilgangTilPerson(ident).throwIfIkkeTilgang()
    }

    fun harTilgangTilPerson(ident: EksternBrukerId): AuthResult {
        return when (val principal = principal()) {
            is EksternBrukerPrincipal -> harEksternBrukerHarTilgang(principal, ident.toFnr())
            is SystemPrincipal -> erSystemkallFraAzureAd(authContextHolder.requireIdTokenClaims(), authContextHolder.role.get())
            is VeilederPrincipal -> internBrukerAuth.harInternbrukerHarLeseTilgangTilPerson(requireInternbrukerOid(), ident.toFnr(), authContextHolder.navIdent.get())
        }
    }

    override fun harTilgangTilEnhet(enhet: EnhetId): Boolean {
        return when {
            erEksternBruker() -> return true
            else -> poaoTilgangClient.evaluatePolicy(NavAnsattTilgangTilNavEnhetPolicyInput(requireInternbrukerOid(), enhet.get())).get()?.isPermit ?: false
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
        internBrukerAuth.sjekkInternbrukerHarSkriveTilgangTilPerson(requireInternbrukerOid(), aktorId, authContextHolder.navIdent.get())
    }

    private fun requireInternbrukerOid(): UUID {
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

sealed class AuthResult {
    class UserSuccessResult(val subjectIdent: Id, val objectIdent: Id): AuthResult()

    class UserFailedResult(val subjectIdent: Id, val objectIdent: Id, val melding: String): AuthResult()
    class UnAuditedSuccessResult(): AuthResult()
    class UnAuditedFailedResult(): AuthResult()
}

fun AuthResult.throwIfIkkeTilgang() {
    when(this) {
        is AuthResult.UserFailedResult -> throw ResponseStatusException(HttpStatus.FORBIDDEN, this.melding)
        is AuthResult.UnAuditedFailedResult -> throw ResponseStatusException(HttpStatus.FORBIDDEN)
        is AuthResult.UserSuccessResult, is AuthResult.UnAuditedSuccessResult -> return
    }
}