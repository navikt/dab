plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
    `maven-publish`
    java
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

kotlin {
    jvmToolchain(17)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}


tasks.named<Test>("test") {
    useJUnitPlatform() // <5>
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
