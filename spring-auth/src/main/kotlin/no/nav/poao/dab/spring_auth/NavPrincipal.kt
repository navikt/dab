package no.nav.poao.dab.spring_auth

import com.nimbusds.jwt.JWTClaimsSet
import no.nav.common.auth.Constants
import no.nav.common.auth.Constants.ID_PORTEN_PID_CLAIM
import no.nav.common.auth.context.UserRole
import no.nav.common.types.identer.EksternBrukerId
import no.nav.common.types.identer.NavIdent

sealed class NavPrincipal(
    val jwtClaimsSet: JWTClaimsSet
) {
    fun hasLevel4() = jwtClaimsSet.getStringClaim("acr") == "Level4"
    private fun isAzure() = jwtClaimsSet.issuer.contains("microsoftonline.com")
    private fun isTokenX() = jwtClaimsSet.issuer.contains("tokendings")

    protected fun getFullAppName(): String? { //  "cluster:team:app"
        return when {
            isAzure() -> jwtClaimsSet.getStringClaim("azp_name")
            isTokenX() -> jwtClaimsSet.getStringClaim("client_id")
            else -> jwtClaimsSet.subject
        }
    }

    companion object {
        fun of(jwtClaimsSet: JWTClaimsSet, userRole: UserRole): NavPrincipal {
            return when (userRole) {
                UserRole.INTERN -> VeilederPrincipal(jwtClaimsSet)
                UserRole.EKSTERN -> EksternBrukerPrincipal(jwtClaimsSet)
                UserRole.SYSTEM -> SystemPrincipal(jwtClaimsSet)
            }
        }
    }
}

class VeilederPrincipal(jwtClaimsSet: JWTClaimsSet): NavPrincipal(jwtClaimsSet) {
    val userRole = UserRole.INTERN
    fun navIdent() = NavIdent.of(jwtClaimsSet.getStringClaim(Constants.AAD_NAV_IDENT_CLAIM))
}
class EksternBrukerPrincipal(jwtClaimsSet: JWTClaimsSet): NavPrincipal(jwtClaimsSet) {
    val userRole = UserRole.EKSTERN
    fun brukerIdent() = EksternBrukerId.of(jwtClaimsSet.getStringClaim(ID_PORTEN_PID_CLAIM))
}
class SystemPrincipal(jwtClaimsSet: JWTClaimsSet): NavPrincipal(jwtClaimsSet) {
    val userRole = UserRole.SYSTEM
    fun systemIdent() = this.getFullAppName()
}