package no.nav.poao.dab.ktor_oauth_client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
    @SerialName("token_type")
    val tokenType: String,
): TokenResult()

@Serializable
data class TokenResponseOauthError(
    @SerialName("error")
    val error: String,
    @SerialName("error_description")
    val errorDescription: String,
    @SerialName("error_codes")
    val errorCodes: List<Long>,
    @SerialName("timestamp")
    val timestamp: String,
    @SerialName("trace_id")
    val traceId: String
): TokenResult()

@Serializable
data class TokenResponseError(
    @SerialName("error")
    val error: String,
): TokenResult()

@Serializable
sealed class TokenResult()
