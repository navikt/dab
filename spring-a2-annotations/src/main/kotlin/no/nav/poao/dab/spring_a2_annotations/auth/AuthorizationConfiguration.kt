package no.nav.poao.dab.spring_a2_annotations.auth

import no.nav.poao.dab.spring_a2_annotations.EnableAuthorization
import no.nav.poao.dab.spring_auth.AuthService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportAware
import org.springframework.core.annotation.AnnotationAttributes
import org.springframework.core.type.AnnotationMetadata

@Configuration
open class AuthorizationConfiguration : ImportAware {
    private lateinit var attrs: AnnotationAttributes

    @Bean
    open fun authorizationInterceptor(authService: AuthService): AuthorizationInterceptor {
        return AuthorizationInterceptor(attrs, authService)
    }

    @Bean
    open fun authorizationWebMvcConfigurer(): AuthorizationWebMvcConfigurer {
        return AuthorizationWebMvcConfigurer()
    }

    override fun setImportMetadata(meta: AnnotationMetadata) {
        attrs = AnnotationAttributes.fromMap(
            meta.getAnnotationAttributes(
                EnableAuthorization::class.java.name,
                false
            )
        ) ?: throw IllegalArgumentException("@EnableJwtTokenValidation is not present on importing class $meta.className")
    }
}
