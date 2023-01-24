package no.nav.poao.dab.spring_a2_annotations.auth

import com.nimbusds.jwt.JWTClaimsSet
import java.util.function.Consumer

object Authorizers {
    val tokenXM2MAuthorizer: Consumer<JWTClaimsSet> = Consumer<JWTClaimsSet> { claims: JWTClaimsSet? -> }
    val tokenxOBOAuthorizer: Consumer<JWTClaimsSet> = Consumer<JWTClaimsSet> { claims: JWTClaimsSet? -> }
    val azureM2MAuthorizer: Consumer<JWTClaimsSet> = Consumer<JWTClaimsSet> { claims: JWTClaimsSet? -> }
    val azureOBOAuthorizer: Consumer<JWTClaimsSet> = Consumer<JWTClaimsSet> { claims: JWTClaimsSet? -> }
}