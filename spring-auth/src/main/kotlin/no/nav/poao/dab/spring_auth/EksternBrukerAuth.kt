package no.nav.poao.dab.spring_auth;

import no.nav.common.types.identer.Fnr
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

object EksternBrukerAuth {
    fun sjekkEksternBrukerHarTilgang(navPrincipal: EksternBrukerPrincipal, ident: Fnr) {
        if (navPrincipal.brukerIdent() != ident) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "ekstern bruker har ikke tilgang til andre brukere enn seg selv"
            )
        }
        if (!navPrincipal.hasLevel4()) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "ekstern bruker har ikke innloggingsnivå 4"
            )
        }
    }
}
