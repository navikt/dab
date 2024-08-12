package no.nav.poao.dab.spring_a2_annotations.auth

import no.nav.common.types.identer.EnhetId
import no.nav.common.types.identer.Fnr
import kotlin.reflect.KClass

/**
 * Declares what type of resource the caller wants to access
 */
interface ResourceType {}

interface NoResource : ResourceType {}
/**
 * Get owner of resource with id resourceId of type ResourceType
 */
interface OwnerProvider {
    fun getOwner(resourceId: String, resourceType: KClass<out ResourceType>): OwnerResult
}

sealed interface OwnerResult

data class OwnerResultSuccess(
    val fnr: Fnr,
    val enhetId: EnhetId?
): OwnerResult

data object ResourceNotFound: OwnerResult

class OwnerNotFoundException(message: String): Exception(message)
