plugins {
    id("dab.kotlin-libary")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    implementation(libs.nav.common.auth)
    implementation(libs.nav.common.abac)
    implementation(libs.nav.common.types)

    implementation(libs.slf4j)
    implementation(libs.nimbusds.jose.jwt)

    implementation(libs.spring.context)
    implementation(libs.spring.web)

    testImplementation(libs.junit.kotlin)
    testImplementation(libs.junit.engine)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.runner)
}
