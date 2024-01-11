package no.nav.poao.dab.ktor_oauth_client

import java.time.LocalDateTime

data class AccessTokenWrapper(
    val scope: String,
    val token: String,
    val expires: LocalDateTime
) {
    fun hasExpired(): Boolean {
        val marginSeconds = 1L
        return LocalDateTime.now().isAfter(expires.plusSeconds(marginSeconds))
    }
}
