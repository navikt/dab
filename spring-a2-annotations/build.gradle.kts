dependencies {
    implementation(libs.nav.common.types)
    implementation(libs.spring.context)
    implementation(libs.spring.webmcvc)
    implementation(libs.servlet.api)
    implementation(libs.slf4j)
    implementation(libs.nimbusds.jose.jwt)
    implementation(project(mapOf("path" to ":spring-auth")))
}
