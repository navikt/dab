package no.nav.poao.dab.spring_auth;

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.oauth2.sdk.ParseException
import no.nav.common.auth.context.UserRole

object SystemAuth {
    fun erSystemkallFraAzureAd(claims: JWTClaimsSet, role: UserRole): AuthResult.UnAuditedResult {
        val erSystemkallFraAzureAd = UserRole.SYSTEM == role && harAADRolleForSystemTilSystemTilgang(claims)
        return AuthResult.UnAuditedResult(harTilgang = erSystemkallFraAzureAd)
    }

    private fun harAADRolleForSystemTilSystemTilgang(claims: JWTClaimsSet): Boolean {
        return try {
            claims
                .getStringListClaim("roles")
                ?.contains("access_as_application") ?: false
        } catch (e: ParseException) {
            false
        }
    }
}
