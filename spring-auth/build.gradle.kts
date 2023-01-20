plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    kotlin("jvm")
}

val commonVersion = "2.2023.01.10_13.49-81ddc732df3a"
val springFramework = "5.3.24"

dependencies {
    implementation("no.nav.common:types:${commonVersion}")
    implementation("no.nav.common:abac:${commonVersion}")
    implementation("no.nav.common:auth:${commonVersion}")

    implementation("org.springframework:spring-context:${springFramework}")
    implementation("org.springframework:spring-web:${springFramework}")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.1")
}
