package no.nav.poao.dab.ktor_oauth_client

interface TokenCache {
    fun get(scope: Scope): AccessToken?
    fun get(scope: Scope, incomingToken: IncomingToken): AccessToken?
    fun set(scope: Scope, accessTokenWrapper: AccessTokenWrapper)
    fun set(scope: Scope, incomingToken: IncomingToken, accessTokenWrapper: AccessTokenWrapper)
}
