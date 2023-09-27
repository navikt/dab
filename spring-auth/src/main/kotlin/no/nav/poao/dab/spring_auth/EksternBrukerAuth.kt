package no.nav.poao.dab.spring_auth;

import no.nav.common.types.identer.Fnr

object EksternBrukerAuth {
    fun harEksternBrukerHarTilgang(navPrincipal: EksternBrukerPrincipal, ident: Fnr): Resoult {
        return if (navPrincipal.brukerIdent() != ident) {
            Resoult(harTilgang = false, accesedIdnet = navPrincipal.brukerIdent(), byIdent= ident.get(), melding = "ekstern bruker har ikke tilgang til andre brukere enn seg selv")
        } else if (!navPrincipal.hasLevel4()) {
            Resoult(harTilgang = false, accesedIdnet = navPrincipal.brukerIdent(), byIdent= ident.get(), melding = "ekstern bruker har ikke innloggingsnivå 4")
        } else {
            Resoult(harTilgang = true, accesedIdnet = navPrincipal.brukerIdent(), byIdent= ident.get(), melding = null)
        }

    }
}
