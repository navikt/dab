package no.nav.poao.dab.spring_a2_annotations.filter

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.util.StreamUtils
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.io.IOException

@Component
@WebFilter(filterName = "FnrAktorIdAttributeFilter", urlPatterns = ["/*"])
class FnrAktorIdAttributeFilter : OncePerRequestFilter() {
    val logger = LoggerFactory.getLogger("no.nav.poao.dab.spring_a2_annotations.filter.FnrAktorIdAttributeFilter")

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        httpServletRequest: HttpServletRequest,
        httpServletResponse: HttpServletResponse,
        filterChain: FilterChain
    ) {
        println("IN  FnrAktorIdAttributeFilter ")
        val fnr = httpServletRequest.getParameter("fnr") ?: readJsonAttribute(httpServletRequest, "fnr")
        ?: throw ResponseStatusException(
            HttpStatus.FORBIDDEN, "Missing fnr in parameter or body"
        )
        httpServletRequest.setAttribute("fnr", fnr)
        filterChain.doFilter(httpServletRequest, httpServletResponse)
    }

    private val jsonFactory = JsonFactory()

    internal fun readJsonAttribute(request: HttpServletRequest, attributeName: String): String? {
        if (request !is CachedBodyHttpServletRequest) {
            logger.warn("Need ContentCachingFilter to read fnr/aktorId from body in post/put requests.")
            return null;
        }

        val eventReader = jsonFactory.createParser(request.inputStream)

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
}
