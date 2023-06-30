package no.nav.poao.dab.spring_auth;

import no.nav.common.types.identer.EksternBrukerId
import no.nav.poao_tilgang.client.NavAnsattTilgangTilEksternBrukerPolicyInput
import no.nav.poao_tilgang.client.PoaoTilgangClient
import no.nav.poao_tilgang.client.TilgangType
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

internal class InternBrukerAuth(private val pep: PoaoTilgangClient, private val personService: IPersonService) {
    fun sjekkInternbrukerHarSkriveTilgangTilPerson(navIdent: UUID, aktorId: EksternBrukerId) = sjekkTilgang(navIdent,  aktorId, TilgangType.SKRIVE)
    fun sjekkInternbrukerHarLeseTilgangTilPerson(navIdent: UUID, aktorId: EksternBrukerId) = sjekkTilgang(navIdent,  aktorId, TilgangType.LESE)
    fun sjekkTilgang(navIdent: UUID, aktorId: EksternBrukerId, actionId: TilgangType) {
        val fnr = personService.getFnrForAktorId(aktorId).get()
        val evaluatePolicy = pep.evaluatePolicy(NavAnsattTilgangTilEksternBrukerPolicyInput(navIdent, actionId, fnr))
        val harTilgang = evaluatePolicy.get()?.isPermit ?: false
        if (!harTilgang) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }
}
