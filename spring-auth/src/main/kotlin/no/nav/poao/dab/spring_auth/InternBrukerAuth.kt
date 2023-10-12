package no.nav.poao.dab.spring_auth;

import no.nav.common.types.identer.EksternBrukerId
import no.nav.common.types.identer.NavIdent
import no.nav.poao_tilgang.client.NavAnsattTilgangTilEksternBrukerPolicyInput
import no.nav.poao_tilgang.client.PoaoTilgangClient
import no.nav.poao_tilgang.client.TilgangType
import java.util.*

internal class InternBrukerAuth(private val pep: PoaoTilgangClient, private val personService: IPersonService) {
    fun sjekkInternbrukerHarSkriveTilgangTilPerson(azureNavId: UUID, aktorId: EksternBrukerId, navIdent: NavIdent): Unit = harTilgang(azureNavId,  aktorId, TilgangType.SKRIVE, navIdent).throwIfIkkeTilgang()
    fun harInternbrukerHarLeseTilgangTilPerson(azureNavId: UUID, aktorId: EksternBrukerId, navIdent: NavIdent):  AuthResult = harTilgang(azureNavId,  aktorId, TilgangType.LESE, navIdent)
    fun harTilgang(azureNavId: UUID, aktorId: EksternBrukerId, actionId: TilgangType, navIdent: NavIdent): AuthResult {
        val fnr = personService.getFnrForAktorId(aktorId).get()
        val evaluatePolicy = pep.evaluatePolicy(NavAnsattTilgangTilEksternBrukerPolicyInput(azureNavId, actionId, fnr))
        val harTilgang = evaluatePolicy.get()?.isPermit ?: false

        return if(harTilgang) {
            AuthResult.UserSuccessResult(subjectIdent = navIdent, objectIdent = aktorId)
        } else {
            AuthResult.UserFailedResult(subjectIdent = navIdent, objectIdent =  aktorId, "")
        }
    }
}
