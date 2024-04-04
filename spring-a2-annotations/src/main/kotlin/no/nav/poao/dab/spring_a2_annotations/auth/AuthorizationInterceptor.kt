package no.nav.poao.dab.spring_a2_annotations.auth


import no.nav.poao.dab.spring_auth.AuthService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.HandlerInterceptor
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class AuthorizationInterceptor(authService: AuthService, ownerProvider: OwnerProvider) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)
    private val annotationHandler: AuthorizationAnnotationHandler

    init {
        annotationHandler = AuthorizationAnnotationHandler(authService, ownerProvider)
    }

    @Throws(Exception::class)
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if (handler is HandlerMethod) {
            try {
                annotationHandler.doAuthorizationCheckIfTagged(handler.method, request)
            } catch (e: Exception) {
                if (e is ResponseStatusException) {
                    throw e
                }

                log.error("Failed to process annotation", e)
                throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
        return true
    }
}
