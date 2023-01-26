package no.nav.poao.dab.spring_auth

import no.nav.common.types.identer.AktorId
import no.nav.common.types.identer.EksternBrukerId
import no.nav.common.types.identer.Fnr

interface IPersonService {
    fun getFnrForAktorId(aktorId: EksternBrukerId): Fnr
    fun getAktorIdForPersonBruker(eksternBrukerId: EksternBrukerId): AktorId
}