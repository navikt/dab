package no.nav.poao.dab.spring_auth;

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.oauth2.sdk.ParseException
import no.nav.common.auth.context.UserRole
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

object SystemAuth {
    fun harErSystemkallFraAzureAd(claims: JWTClaimsSet, role: UserRole): SystemResoult {
        return SystemResoult(harTilgang = !erSystemkallFraAzureAd(claims, role))
    }

    fun erSystemkallFraAzureAd(claims: JWTClaimsSet, role: UserRole): Boolean {
        return UserRole.SYSTEM == role && harAADRolleForSystemTilSystemTilgang(claims)
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
