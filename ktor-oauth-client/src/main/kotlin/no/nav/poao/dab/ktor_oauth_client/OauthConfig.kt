package no.nav.poao.dab.ktor_oauth_client


typealias ClientSecret = String
data class OauthClientCredentialsConfig(
    val clientId: String,
    val clientSecret: ClientSecret,
    val tokenEndpoint: String) {
    init {
        require(clientId.isNotBlank()) { "ClientId can not be null" }
        require(clientSecret.isNotBlank()){ "ClientSecret can not be null" }
        require(tokenEndpoint.isNotBlank()) { "TokenEndpoint can not be null" }
    }
}
