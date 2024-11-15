plugins {
    id("dab.kotlin-libary")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.kotlinx)
    implementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.junit.kotlin)
}
