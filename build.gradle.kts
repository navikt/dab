/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin library project to get you started.
 * For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle
 * User Manual available at https://docs.gradle.org/7.6/userguide/building_java_projects.html
 */

plugins {
    kotlin("jvm") version "1.8.0"
    `java-library`
    `maven-publish`
}

java.sourceCompatibility = JavaVersion.VERSION_17

allprojects {
    repositories {
        // Use Maven Central for resolving dependencies.
        mavenCentral()
        maven("https://jitpack.io")
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}

subprojects {
    group = "com.github.navikt"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    java.sourceCompatibility = JavaVersion.VERSION_17


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
}
