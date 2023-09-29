package no.nav.poao.dab.spring_a2_annotations.auth

@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CLASS
)
annotation class AuthorizeFnr(val allowlist: Array<String> = [], val auditlogMessage: String = "")
