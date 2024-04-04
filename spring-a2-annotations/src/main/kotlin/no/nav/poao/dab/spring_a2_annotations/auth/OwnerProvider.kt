package no.nav.poao.dab.spring_a2_annotations.auth

import no.nav.common.types.identer.EnhetId
import no.nav.common.types.identer.Fnr

interface OwnerProvider {
    fun getOwner(resourceId: String): OwnerResult
}

sealed interface OwnerResult

data class OwnerResultSuccess(
    val fnr: Fnr,
    val enhetId: EnhetId?
): OwnerResult

data object ResourceNotFound: OwnerResult
