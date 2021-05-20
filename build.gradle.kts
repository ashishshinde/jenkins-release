/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin library project to get you started.
 */

import net.researchgate.release.ReleaseExtension

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.3.72"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    `maven-publish`

    id("net.researchgate.release") version "2.8.1"
}

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
}

tasks.getByName("afterReleaseBuild").dependsOn("publish")

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.ashishshinde"
            artifactId = "jenkins-release"
            version = project.version.toString()

            from(components["java"])
        }
    }
    repositories {
        val repositoryUrl =
            uri("https://maven.pkg.github.com/ashishshinde/jenkins-release")

        val pkgRepoUser: String by project
        val pkgRepoPassword: String by project

        maven {
            name = "AerospikeMavenRepo"
            url = repositoryUrl
            credentials {
                username = pkgRepoUser
                password = pkgRepoPassword
            }
        }
    }
}

project.extensions.configure(ReleaseExtension::class) {
    tagTemplate = "\$name-\$version"
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}
