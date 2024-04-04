package no.nav.poao.dab.spring_a2_annotations.filter

import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import org.springframework.util.StreamUtils
import java.io.*

class CachedBodyHttpServletRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    private val cachedBody: ByteArray

    init {
        val requestInputStream: InputStream = request.inputStream
        this.cachedBody = StreamUtils.copyToByteArray(requestInputStream)
    }

    @Throws(IOException::class)
    override fun getInputStream(): ServletInputStream {
        return CachedBodyServletInputStream(this.cachedBody)
    }

    @Throws(IOException::class)
    override fun getReader(): BufferedReader {
        // Create a reader from cachedContent
        // and return it
        val byteArrayInputStream = ByteArrayInputStream(this.cachedBody)
        return BufferedReader(InputStreamReader(byteArrayInputStream))
    }
}