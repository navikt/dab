package no.nav.poao.dab.spring_a2_annotations.auth

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
open class AuthorizationWebMvcConfigurer : WebMvcConfigurer {

    @Autowired
    lateinit var authorizationInterceptor: AuthorizationInterceptor
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authorizationInterceptor)
    }
}