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
import org.springframework.core.annotation.AnnotationAttributes
import java.util.concurrent.ConcurrentHashMap


@Component
class AuthorizationInterceptor(attrs: AnnotationAttributes?, authService: AuthService) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)
    private val annotationHandler: AuthorizationAnnotationHandler

    private val handlerFlags: MutableMap<Any, Boolean> = ConcurrentHashMap()
    private val ignoreConfig = attrs?.getStringArray("ignore") ?: arrayOfNulls(0) ?: arrayOfNulls(0)

    init {
        annotationHandler = AuthorizationAnnotationHandler(authService)
    }

    @Throws(Exception::class)
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if (handler is HandlerMethod) {
            return if (shouldIgnore(handler.bean)) {
                true
            } else try {
                annotationHandler.doAuthorizationCheckIfTagged(handler.method, request)
                return true
            } catch (e: Exception) {
                // Catch all exception except status-exceptions
                if (e is ResponseStatusException) {
                    throw e
                }
                log.error("Failed to process annotation", e)
                throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR)
            }
        }
        return true
    }

    private fun shouldIgnore(o: Any): Boolean {
        val flag = handlerFlags[o]
        if (flag != null) {
            return flag
        }
        val fullName = o.javaClass.name
        ignoreConfig.forEach { ignore ->
            if (fullName.startsWith(ignore)) {
                log.trace("Adding $fullName to OIDC validation ignore list")
                handlerFlags[o] = true
                return true
            }
        }
        log.trace("Adding $fullName to OIDC validation interceptor list")
        handlerFlags[o] = false
        return false
    }


}
