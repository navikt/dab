package no.nav.poao.dab.ktor_oauth_client

class SimpleTokenCache: TokenCache {
    private val accessTokens: MutableMap<String, AccessTokenWrapper> = mutableMapOf()

    override fun get(scope: Scope): AccessToken? {
        return accessTokens[scope]
            ?.let { if (it.hasExpired()) null else it }
            ?.token
    }

    override fun get(scope: Scope, incomingToken: IncomingToken): AccessToken? {
        return accessTokens[incomingToken.toCacheKey()]
            ?.let { if (it.hasExpired()) null else it }
            ?.token
    }

    override fun set(scope: Scope, accessTokenWrapper: AccessTokenWrapper) {
        accessTokens[scope] = accessTokenWrapper
    }

    override fun set(scope: Scope, incomingToken: IncomingToken, accessTokenWrapper: AccessTokenWrapper) {
        accessTokens[incomingToken.toCacheKey()] = accessTokenWrapper
    }
}

private fun IncomingToken.toCacheKey(): String {
    // Use JWT signature as cache-key
    return this.split(".").last()
}