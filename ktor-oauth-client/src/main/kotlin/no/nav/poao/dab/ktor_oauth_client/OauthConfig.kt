package no.nav.poao.dab.ktor_oauth_client

data class OauthClientCredentialsConfig(
    val clientId: String,
    val clientSecret: String,
    val tokenEndpoint: String)
