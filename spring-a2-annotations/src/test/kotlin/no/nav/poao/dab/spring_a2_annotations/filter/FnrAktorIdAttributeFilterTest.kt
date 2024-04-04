package no.nav.poao.dab.spring_a2_annotations.filter

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test

class FnrAktorIdAttributeFilterTest {
    private val fnrAktorIdAttributeFilter = FnrAktorIdAttributeFilter()
    @Test
    fun `Skal kunne lese fnr fra request body`() {
        val json = this::class.java.getResource("/personidentifikatorPaaToppnivaa.json")?.readText()!!
        val servletRequest: CachedBodyHttpServletRequest = mockk()
        every { servletRequest.inputStream } returns MockServletInputStream(json)

        val fnr = fnrAktorIdAttributeFilter.readJsonAttribute(servletRequest, "fnr")

        fnr shouldBe "01010112345"
    }

    @Test
    fun `Skal kunne lese aktorId fra request body`() {
        val json = this::class.java.getResource("/personidentifikatorPaaToppnivaa.json")?.readText()!!
        val servletRequest: CachedBodyHttpServletRequest = mockk()
        every { servletRequest.inputStream } returns MockServletInputStream(json)

        val fnr = fnrAktorIdAttributeFilter.readJsonAttribute(servletRequest, "aktorId")

        fnr shouldBe "1234567890123"
    }

    @Test
    fun `Skal ignorere fnr som ikke ligger på topp-nivå`() {
        val json = this::class.java.getResource("/personidentifikatorIkkePaaToppnivaa.json")?.readText()!!
        val servletRequest: CachedBodyHttpServletRequest = mockk()
        every { servletRequest.inputStream } returns MockServletInputStream(json)

        val fnr = fnrAktorIdAttributeFilter.readJsonAttribute(servletRequest, "fnr")

        fnr shouldBe null
    }

    @Test
    fun `Skal ignorere aktorId som ikke ligger på topp-nivå`() {
        val json = this::class.java.getResource("/personidentifikatorIkkePaaToppnivaa.json")?.readText()!!
        val servletRequest: CachedBodyHttpServletRequest = mockk()
        every { servletRequest.inputStream } returns MockServletInputStream(json)

        val fnr = fnrAktorIdAttributeFilter.readJsonAttribute(servletRequest, "aktorId")

        fnr shouldBe null
    }

    @Test
    fun `Skal kunne lese fnr i request body som ligger på toppnivå etter et objekt`() {
        val json = this::class.java.getResource("/personidentifikatorEtterObjekt.json")?.readText()!!
        val servletRequest: CachedBodyHttpServletRequest = mockk()
        every { servletRequest.inputStream } returns MockServletInputStream(json)

        val fnr = fnrAktorIdAttributeFilter.readJsonAttribute(servletRequest, "fnr")

        fnr shouldBe "01010112345"
    }

    @Test
    fun `Skal kunne lese aktorId i request body som ligger på toppnivå etter et objekt`() {
        val json = this::class.java.getResource("/personidentifikatorEtterObjekt.json")?.readText()!!
        val servletRequest: CachedBodyHttpServletRequest = mockk()
        every { servletRequest.inputStream } returns MockServletInputStream(json)

        val fnr = fnrAktorIdAttributeFilter.readJsonAttribute(servletRequest, "aktorId")

        fnr shouldBe "1234567890123"
    }

    class MockServletInputStream(private val body: String) : ServletInputStream() {
        private var index = 0

        override fun read(): Int {
            return if (index < body.length) {
                body[index++].toInt()
            } else {
                -1
            }
        }

        override fun isFinished(): Boolean = TODO("Not yet implemented")

        override fun isReady(): Boolean = TODO("Not yet implemented")

        override fun setReadListener(p0: ReadListener?) = TODO("Not yet implemented")
    }
}