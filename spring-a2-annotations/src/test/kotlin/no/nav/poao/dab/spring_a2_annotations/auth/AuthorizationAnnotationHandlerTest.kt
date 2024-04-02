package no.nav.poao.dab.spring_a2_annotations.auth

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import no.nav.poao.dab.spring_auth.AuthService
import org.junit.jupiter.api.Test

class AuthorizationAnnotationHandlerTest {
    private val authService: AuthService = mockk()
    private val authorizationAnnotationHandler = AuthorizationAnnotationHandler(authService)

    @Test
    fun `Skal kunne lese fnr fra request body`() {
        val json = this::class.java.getResource("/fnrPaaToppnivaa.json")?.readText()!!
        val inputStream = MockServletInputStream(json)
        val servletRequest: HttpServletRequest = mockk()
        every { servletRequest.inputStream } returns inputStream

        val fnr = authorizationAnnotationHandler.readJsonAttribute(servletRequest, "fnr")

        fnr shouldBe "01010112345"
    }

    @Test
    fun `Skal ignorere fnr som ikke ligger på top-nivå`() {
        val json = this::class.java.getResource("/fnrIkkePaaToppnivaa.json")?.readText()!!
        val inputStream = MockServletInputStream(json)
        val servletRequest: HttpServletRequest = mockk()
        every { servletRequest.inputStream } returns inputStream

        val fnr = authorizationAnnotationHandler.readJsonAttribute(servletRequest, "fnr")

        fnr shouldBe null
    }

    @Test
    fun `Skal kunne lese fnr i request body som ligger på toppnivå etter et objekt`() {
        val json = this::class.java.getResource("/fnrEtterObjekt.json")?.readText()!!
        val inputStream = MockServletInputStream(json)
        val servletRequest: HttpServletRequest = mockk()
        every { servletRequest.inputStream } returns inputStream

        val fnr = authorizationAnnotationHandler.readJsonAttribute(servletRequest, "fnr")

        fnr shouldBe "01010112345"
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

        override fun isFinished(): Boolean {
            TODO("Not yet implemented")
        }

        override fun isReady(): Boolean {
            TODO("Not yet implemented")
        }

        override fun setReadListener(p0: ReadListener?) {
            TODO("Not yet implemented")
        }
    }
}