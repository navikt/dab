plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    kotlin("jvm")
    `maven-publish`
}

val commonVersion = "2.2023.01.10_13.49-81ddc732df3a"
val springFramework = "5.3.24"
val junit = "5.9.1"
val kotest = "5.5.4"

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

apply(plugin = "java-library")
apply(plugin = "maven-publish")

dependencies {
    implementation("no.nav.common:types:${commonVersion}")
    implementation("no.nav.common:abac:${commonVersion}")
    implementation("no.nav.common:auth:${commonVersion}")

    implementation("org.springframework:spring-context:${springFramework}")
    implementation("org.springframework:spring-web:${springFramework}")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:${junit}")
    testImplementation("io.kotest:kotest-runner-junit5:${kotest}")
    testImplementation("io.kotest:kotest-assertions-core:$kotest")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(sourcesJar.get())

            pom {
                description.set("DAB felles")
                name.set(project.name)
                withXml {
                    asNode().appendNode("packaging", "jar")
                }
                licenses {
                    license {
                        name.set("MIT License")
                        name.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        organization.set("NAV (Arbeids- og velferdsdirektoratet) - The Norwegian Labour and Welfare Administration")
                        organizationUrl.set("https://www.nav.no")
                    }
                }
            }
        }
    }
}