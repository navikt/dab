plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    kotlin("jvm")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.1")
}
