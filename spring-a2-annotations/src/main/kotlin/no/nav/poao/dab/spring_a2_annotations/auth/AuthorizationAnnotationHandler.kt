package no.nav.poao.dab.spring_a2_annotations.auth

import jakarta.servlet.http.HttpServletRequest
import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.Fnr
import no.nav.poao.dab.spring_auth.AuthService
import no.nav.poao.dab.spring_auth.throwIfIkkeTilgang
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Method
import kotlin.reflect.KClass


class AuthorizationAnnotationHandler(private val authService: AuthService, private val ownerProvider: OwnerProvider) {
    private fun authorizeRequest(annotation: Annotation, request: HttpServletRequest) {
        when (annotation) {
            is AuthorizeFnr -> {
                val resourceType = annotation.resourceType
                val fnr = when {
                    authService.erEksternBruker() -> authService.getLoggedInnUser() as Fnr
                    resourceType == NoResource::class -> request.getParameter("fnr")
                        ?.let { Fnr.of(it) }
                        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Mangler fnr query parameter")
                    else -> {
                        val resourceId = request.getParameterValueFromPathOrQueryByName(annotation.resourceIdParamName)
                            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Fant ingen ressurs-id")
                        getFnr(resourceId, resourceType)
                    }
                }
                val allowlist = annotation.allowlist
                val auditlogMessage = annotation.auditlogMessage
                authorizeFnr(fnr, allowlist, auditlogMessage)
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
    private fun getFnr(resourceId: String, resourceType: KClass<out ResourceType>): Fnr {
        return if(authService.erEksternBruker()) {
            authService.getLoggedInnUser() as? Fnr ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "User ID not Fnr")
        } else {
            val resourceOwner = ownerProvider.getOwner(resourceId, resourceType)
            return when (resourceOwner) {
                is OwnerResultSuccess -> resourceOwner.fnr
                is ResourceNotFound -> throw ResponseStatusException(HttpStatus.FORBIDDEN, "Unknown resource")
            }
        }
    }

    private fun HttpServletRequest.getParameterValueFromPathOrQueryByName(paramName: String): String? {
        val pathParam = (this.getAttribute("org.springframework.web.servlet.HandlerMapping.uriTemplateVariables") as Map<String, String>)[paramName]
        val queryParam =  this.getParameter(paramName)
        return pathParam ?: queryParam
    }

    companion object {
        private val SUPPORTED_ANNOTATIONS = listOf(
            AuthorizeFnr::class,
            AuthorizeAktorId::class,
            OnlyInternBruker::class
        )
    }
}
