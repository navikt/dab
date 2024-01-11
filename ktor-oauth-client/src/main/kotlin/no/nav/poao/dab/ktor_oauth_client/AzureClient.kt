package no.nav.poao.dab.ktor_oauth_client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

val logger = LoggerFactory.getLogger("no.nav.dab.poao.azureAuth.AzureClient.kt")

class AzureClient(val config: OauthClientCredentialsConfig) {
    private val grantType = "client_credentials"
    private val accessTokens: MutableMap<String, AccessToken> = mutableMapOf()

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
    }

    private suspend fun fetchToken(scope: String): TokenResult {
        val (clientId, clientSecret, tokenEndpoint) = config
        return runCatching {
            val res = httpClient.post(tokenEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                formData {
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("scope", scope)
                    append("grant_type", grantType)
                }
            }
            return when {
                res.status.isSuccess() -> res.body<TokenResult>()
                else -> res.body<TokenResponseOauthError>()
                    .also { logger.error("Failed to fetch token: $it", ) }
            }
        }.getOrElse {
            logger.error("Failed to fetch token", it)
            TokenResponseError(it.message ?: "Failed to fetch token")
        }
    }

    private suspend fun fetchAndStoreAccessToken(scope: String): AccessToken {
        val response = fetchToken(scope)
        val tokenResponse = when (response) {
            is TokenResponse -> response
            else -> {
                throw Throwable("Failed to get token: ${response}", )
            }
        }
        val accessToken = AccessToken(
            scope = scope,
            token = tokenResponse.accessToken,
            expires = LocalDateTime.now().plusSeconds(tokenResponse.expiresIn)
        )
        accessTokens[scope] = accessToken
        return accessToken
    }

    suspend fun getAccessToken(scope: String): String {
        val existingToken = accessTokens[scope]
        return if (existingToken == null || existingToken.hasExpired()) {
            fetchAndStoreAccessToken(scope).token
        } else {
            existingToken.token
        }
    }
}
