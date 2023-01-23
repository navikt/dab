package no.nav.poao.dab.spring_auth;

import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.Fnr
import no.nav.common.types.identer.Id
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class EksternBrukerAuth(val personService: PersonService) {
    fun sjekkEksternBrukerHarTilgang(navPrincipal: EksternBrukerPrincipal, ident: Id) {
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

    private fun getFnrForEksternBruker(ident: Id): Fnr {
        return when (ident) {
            is Fnr -> ident
            is AktorId -> personService.getFnrForAktorId(AktorId(ident.get()))
            else ->    throw IllegalArgumentException("Kan ikke hente fnr for NAV-ansatte")
        }
    }
}
