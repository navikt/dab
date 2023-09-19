package no.nav.poao.dab.spring_a2_annotations
import no.nav.poao.dab.spring_a2_annotations.auth.AuthorizationConfiguration
import org.springframework.context.annotation.Import
import java.lang.annotation.Inherited
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS

import no.nav.poao.dab.spring_a2_annotations.auth.AuthorizationWebMvcConfigurer
import no.nav.poao.dab.spring_a2_annotations.auth.AuthorizationInterceptor

@Inherited
@Retention(RUNTIME)
@Target(ANNOTATION_CLASS, CLASS)
@Import(AuthorizationConfiguration::class)
annotation class EnableAuthorization()