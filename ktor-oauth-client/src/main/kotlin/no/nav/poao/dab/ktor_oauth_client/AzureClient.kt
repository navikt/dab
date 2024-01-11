package no.nav.poao.dab.ktor_oauth_client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

val logger = LoggerFactory.getLogger("no.nav.dab.poao.azureAuth.AzureClient.kt")

typealias Scope = String
typealias IncomingToken = String
typealias AccessToken = String
enum class GrantType(val value: String) {
    OnBehalfOf("urn:ietf:params:oauth:grant-type:jwt-bearer"),
    ClientCredentials("client_credentials")
}

class AzureClient(val config: OauthClientCredentialsConfig, val tokenCache: TokenCache = SimpleTokenCache()) {
    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    private suspend fun clientCredentialsGrant(scope: Scope): HttpResponse {
        val (clientId, clientSecret, tokenEndpoint) = config
        return httpClient.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", GrantType.ClientCredentials.value)
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("scope", scope)
            }
        )
    }

    private suspend fun onBehalfOfGrant(scope: Scope, assertion: IncomingToken): HttpResponse {
        val (clientId, clientSecret, tokenEndpoint) = config
        return httpClient.submitForm(
            tokenEndpoint,
            parameters {
                append("grant_type", GrantType.OnBehalfOf.value)
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("scope", scope)
                append("requested_token_use", "on_behalf_of")
                append("assertion", assertion)
            }
        )
    }

    private suspend fun fetchToken(scope: Scope, assertion: IncomingToken?): TokenResult {
        return runCatching {
            val res = assertion?.let { onBehalfOfGrant(scope, it) } ?: clientCredentialsGrant(scope)
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

    private suspend fun fetchAndStoreAccessToken(scope: String, assertion: IncomingToken?): AccessTokenWrapper {
        val response = fetchToken(scope, assertion)
        val tokenResponse = when (response) {
            is TokenResponse -> response
            else -> throw Throwable("Failed to get token: $response", )
        }
        val accessTokenWrapper = AccessTokenWrapper(
            scope = scope,
            token = tokenResponse.accessToken,
            expires = LocalDateTime.now().plusSeconds(tokenResponse.expiresIn)
        )
        if (assertion != null) tokenCache.set(scope, assertion, accessTokenWrapper)
        else tokenCache.set(scope, accessTokenWrapper)
        return accessTokenWrapper
    }

    suspend fun getM2MToken(scope: String): AccessToken {
        val existingToken = tokenCache.get(scope)
        return existingToken ?: fetchAndStoreAccessToken(scope, null).token
    }
    suspend fun getOnBehalfOfToken(scope: String, assertion: IncomingToken): AccessToken {
        val existingToken = tokenCache.get(scope, assertion)
        return existingToken ?: fetchAndStoreAccessToken(scope, assertion).token
    }
}
