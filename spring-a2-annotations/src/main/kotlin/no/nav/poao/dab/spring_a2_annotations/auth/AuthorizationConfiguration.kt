package no.nav.poao.dab.spring_a2_annotations.auth

import no.nav.poao.dab.spring_auth.AuthService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class AuthorizationConfiguration(private val ownerProvider: OwnerProvider) {

    @Bean
    open fun authorizationInterceptor(authService: AuthService): AuthorizationInterceptor {
        return AuthorizationInterceptor(authService, ownerProvider)
    }

    @Bean
    open fun authorizationWebMvcConfigurer(): AuthorizationWebMvcConfigurer {
        return AuthorizationWebMvcConfigurer()
    }
}