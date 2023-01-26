package no.nav.poao.dab.spring_auth;

import no.nav.common.abac.Pep
import no.nav.common.abac.domain.request.ActionId
import no.nav.common.types.identer.EksternBrukerId
import no.nav.common.types.identer.NavIdent
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class InternBrukerAuth(val pep: Pep) {
    fun sjekkInternbrukerHarSkriveTilgangTilPerson(navIdent: NavIdent, aktorId: EksternBrukerId) = sjekkTilgang(navIdent,  aktorId, ActionId.WRITE)
    fun sjekkInternbrukerHarLeseTilgangTilPerson(navIdent: NavIdent, aktorId: EksternBrukerId) = sjekkTilgang(navIdent,  aktorId, ActionId.READ)
    fun sjekkTilgang(navIdent: NavIdent, aktorId: EksternBrukerId, actionId: ActionId) {
        val harTilgang = pep.harVeilederTilgangTilPerson(navIdent, actionId, aktorId)
        if (!harTilgang) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }
}
