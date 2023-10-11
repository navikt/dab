package no.nav.poao.dab.spring_auth;

import no.nav.common.types.identer.Fnr

object EksternBrukerAuth {
    fun harEksternBrukerHarTilgang(navPrincipal: EksternBrukerPrincipal, ident: Fnr): AuthResult {
        return if (navPrincipal.brukerIdent() != ident) {
            AuthResult.UserFailedResult(subjectIdent = navPrincipal.brukerIdent(), objectIdent = ident, melding = "ekstern bruker har ikke tilgang til andre brukere enn seg selv")
        } else if (!navPrincipal.hasLevel4()) {
            AuthResult.UserFailedResult(subjectIdent = navPrincipal.brukerIdent(), objectIdent = ident, melding = "ekstern bruker har ikke innloggingsnivå 4")
        } else {
            AuthResult.UserSuccessResult(subjectIdent = navPrincipal.brukerIdent(), objectIdent = ident)
        }
    }
}
