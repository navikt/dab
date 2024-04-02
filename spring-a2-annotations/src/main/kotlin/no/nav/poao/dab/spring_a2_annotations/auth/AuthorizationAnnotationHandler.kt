package no.nav.poao.dab.spring_a2_annotations.auth

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import jakarta.servlet.http.HttpServletRequest
import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.Fnr
import no.nav.poao.dab.spring_auth.AuthService
import no.nav.poao.dab.spring_auth.throwIfIkkeTilgang
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Method
import kotlin.collections.List

class AuthorizationAnnotationHandler(private val authService: AuthService) {
    private fun authorizeRequest(annotation: Annotation, request: HttpServletRequest) {
        authService.getLoggedInnUser()
        when (annotation) {
            is AuthorizeFnr -> {
                val fnr = Fnr.of(getFnr(request))
                val allowlist = annotation.allowlist
                val auditlogMessage = annotation.auditlogMessage
                authorizeFnr(fnr, allowlist, auditlogMessage)
            }
            is AuthorizeAktorId -> {
                val allowlist = annotation.allowlist
                val aktorId = AktorId.of(getAktorId(request))
                authorizeAktorId(aktorId, allowlist)
            }
            is OnlyInternBruker -> {
                if (!authService.erInternBruker())
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "Bare internbruker tillatt")
            }
        }
    }

    private fun authorizeFnr(fnr: Fnr, allowlist: Array<String>, auditlogMessage: String) {
        if (authService.erSystemBrukerFraAzureAd()) {
            authService.sjekkAtApplikasjonErIAllowList(allowlist)
        } else {
            val harTilgangTilPerson = authService.harTilgangTilPerson(fnr)
            if(auditlogMessage.isNotBlank()) {
                authService.logIfNotSystemAccess(harTilgangTilPerson, auditlogMessage)
            }
            harTilgangTilPerson.throwIfIkkeTilgang()
        }
    }

    private fun authorizeAktorId(aktorId: AktorId, allowlist: Array<String>) {
        when {
            authService.erInternBruker() -> authService.sjekkTilgangTilPerson(aktorId)
            authService.erSystemBruker() -> authService.sjekkAtApplikasjonErIAllowList(allowlist)
            authService.erEksternBruker() -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "Eksternbruker ikke tillatt")
        }
    }

    fun doAuthorizationCheckIfTagged(handlerMethod: Method, request: HttpServletRequest) {
        getRelevantAnnotations(handlerMethod) // Skip if not tagged
            .map { annotation: Annotation -> authorizeRequest(annotation, request) }
    }

    protected fun getRelevantAnnotations(method: Method): List<Annotation> {
        return method.annotations.filter { SUPPORTED_ANNOTATIONS.contains(it.annotationClass) } +
                method.declaringClass.annotations.filter { SUPPORTED_ANNOTATIONS.contains(it.annotationClass) }
    }
    /*
    Supports fnr in query parameter or as a top-level attribute in a json body
     */
    private fun getFnr(request: HttpServletRequest): String? {
        return if(authService.erEksternBruker()) {
            authService.getLoggedInnUser().get()
        } else {
            return request.getParameter("fnr") ?: readJsonAttribute(request, "fnr") ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Missing fnr in parameter or body")
        }
    }

    private val jsonFactory = JsonFactory()

    private fun readJsonAttribute(request: HttpServletRequest, attributeName: String): String? {
        val eventReader = jsonFactory.createParser(request.inputStream)

        tailrec fun readToken(token: JsonToken?, level: Int) : String?  {
            if (token == JsonToken.END_OBJECT || token == null) return null

            if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) return readToken(eventReader.nextToken() , level + 1)

            val fieldName = eventReader.currentName()

            return if (level == 0 && fieldName == attributeName) {
                eventReader.nextToken()
                eventReader.text
            } else {
                readToken(eventReader.nextToken(), level)
            }
        }
        return readToken(eventReader.nextToken(), 0)
    }


    private fun getAktorId(request: HttpServletRequest): String {
        return request.getParameter("aktorId")
    }

    companion object {
        private val SUPPORTED_ANNOTATIONS = listOf(
            AuthorizeFnr::class,
            AuthorizeAktorId::class,
            OnlyInternBruker::class
        )
    }
}
