package no.nav.poao.dab.spring_a2_annotations.auth

import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CLASS
)
annotation class AuthorizeFnr(val allowlist: Array<String> = [], val auditlogMessage: String = "", val resourceIdParamName: String = "", val resourceType: KClass<out ResourceType> = NoResource::class)
