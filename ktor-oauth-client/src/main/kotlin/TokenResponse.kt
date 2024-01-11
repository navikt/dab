import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Long,
): TokenResult()

@Serializable
data class TokenResponseOauthError(
    @SerialName("error")
    val error: String,
    @SerialName("error_description")
    val errorDescription: String,
): TokenResult()

@Serializable
data class TokenResponseError(
    @SerialName("error")
    val error: String,
): TokenResult()

@Serializable
sealed class TokenResult()
