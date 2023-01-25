package no.nav.poao.dab.spring_a2_annotations.auth

import org.slf4j.LoggerFactory
import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.Fnr
import no.nav.poao.dab.spring_auth.AuthService
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Method
import java.util.*
import javax.servlet.http.HttpServletRequest

class AuthorizationAnnotationHandler(private val authService: AuthService) {
    private fun authorizeRequest(annotation: Annotation, request: HttpServletRequest) {
        authService.innloggetBrukerIdent ?: throw UnauthorizedException("Missing token")
        if (annotation is AuthorizeFnr) {
            val fnr = Fnr.of(getFnr(request))
            val allowlist = annotation.allowlist
            authorizeFnr(fnr, allowlist)
        } else if (annotation is AuthorizeAktorId) {
            val allowlist = annotation.allowlist
            val aktorId = AktorId.of(getAktorId(request))
            authorizeAktorId(aktorId, allowlist)
        }
    }

    private fun authorizeFnr(fnr: Fnr, allowlist: Array<String>) {
        if (authService.erSystemBrukerFraAzureAd()) {
            authService.sjekkAtApplikasjonErIAllowList(allowlist)
        } else {
            authService.sjekkTilgangTilPerson(fnr)
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
        Optional.ofNullable(getAnnotation(handlerMethod)) // Skip if not tagged
            .ifPresent { annotation: Annotation -> authorizeRequest(annotation, request) }
    }

    protected fun getAnnotation(method: Method): Annotation? {
        return findAnnotation(method.annotations)
            ?: findAnnotation(method.declaringClass.annotations)
    }

    private fun getFnr(request: HttpServletRequest): String {
        /* Get fnr from headers instead of query when supported by clients */
        return request.getParameter("fnr")
    }

    private fun getAktorId(request: HttpServletRequest): String {
        return request.getParameter("aktorId")
    }

    companion object {
        private val SUPPORTED_ANNOTATIONS = listOf(
            AuthorizeFnr::class,
            AuthorizeAktorId::class
        )

        private fun findAnnotation(annotations: Array<Annotation>): Annotation? {
            return annotations.firstOrNull { SUPPORTED_ANNOTATIONS.contains(it.annotationClass) }
        }
    }
}
