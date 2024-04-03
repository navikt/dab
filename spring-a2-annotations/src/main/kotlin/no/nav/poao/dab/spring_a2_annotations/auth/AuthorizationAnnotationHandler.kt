package no.nav.poao.dab.spring_a2_annotations.auth

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import jakarta.servlet.http.HttpServletRequest
import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.Fnr
import no.nav.poao.dab.spring_auth.AuthService
import no.nav.poao.dab.spring_auth.throwIfIkkeTilgang
import org.springframework.http.HttpStatus
import org.springframework.util.StreamUtils
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.lang.reflect.Method


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

    private fun getAktorId(request: HttpServletRequest): String {
        return request.getParameter("aktorId") ?: readJsonAttribute(request, "aktorId") ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Missing aktorId in parameter or body")
    }

    private val jsonFactory = JsonFactory()

    internal fun readJsonAttribute(request: HttpServletRequest, attributeName: String): String? {
        // Copy stream to avoid closing the original input stream
        val inputstreamCopy = ByteArrayInputStream(StreamUtils.copyToByteArray(request.inputStream))
        val eventReader = jsonFactory.createParser(inputstreamCopy)

        fun readToken(token: JsonToken?, level: Int) : String?  {
            if ((token == JsonToken.END_OBJECT && level == 0 )|| token == null) return null
            val fieldName = eventReader.currentName()

            return if (level == 1 && attributeName == fieldName) {
                eventReader.nextToken()
                eventReader.text
            } else {
                val startObject = token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY
                val endObject = token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY
                val nextLevel = if (startObject) level + 1 else if (endObject) level - 1 else level

                readToken(eventReader.nextToken(), nextLevel)
            }
        }

        return readToken(eventReader.nextToken(), 0)
    }

    companion object {
        private val SUPPORTED_ANNOTATIONS = listOf(
            AuthorizeFnr::class,
            AuthorizeAktorId::class,
            OnlyInternBruker::class
        )
    }
}
