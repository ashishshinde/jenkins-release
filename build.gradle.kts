/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin library project to get you started.
 */

import com.github.breadmoirai.githubreleaseplugin.GithubReleaseExtension
import net.researchgate.release.ReleaseExtension
import okhttp3.OkHttpClient

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.0")
        classpath("com.netflix.nebula:gradle-ospackage-plugin:8.3.0")
        classpath("gradle.plugin.com.lazan:java-monkey-patch:1.0")
        classpath("net.researchgate:gradle-release:2.6.0")
        classpath("io.swagger:swagger-codegen:2.3.1")
        classpath("com.github.jengelman.gradle.plugins:shadow:5.2.0")
        classpath("gradle.plugin.io.github.http-builder-ng:http-plugin:0.1.1")
        classpath("com.github.breadmoirai:github-release:2.2.12")
    }
}

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.3.72"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`

    `maven-publish`

    id("net.researchgate.release") version "2.8.1"
    id("com.github.breadmoirai.github-release") version "2.2.12"
}


allprojects {
    apply {
        plugin("java")
        plugin("net.researchgate.release")
        plugin("maven-publish")
        plugin("com.github.breadmoirai.github-release")
    }

    repositories {
        mavenCentral()
        // Use jcenter for resolving dependencies.
        // You can declare any Maven/Ivy/file repository here.
        jcenter()
    }

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

    tasks.getByName("afterReleaseBuild").dependsOn("publish")

    tasks.getByName("githubRelease").dependsOn("release")
    project.extensions.configure(GithubReleaseExtension::class) {
        if (!project.hasProperty("release.releaseVersion")) {
            throw Exception("Project property release.releaseVersion not set")
        }

        val releaseVersion = project.property("release.releaseVersion")
        token(System.getenv("GITHUB_TOKEN"))
        owner("ashishshinde")
        repo("jenkins-release")
        tagName("${project.name}-$releaseVersion")


        val releaseName = "${
            project.name.split("-").map { it.capitalize() }
                .joinToString(" ")
        } $releaseVersion"
        releaseName(releaseName)

        if (project.hasProperty("releaseNotesFile")) {
            body(
                File(
                    project.property("releaseNotesFile").toString()
                ).readText()
            )
        }

        releaseAssets(project.artifacts)
        apiEndpoint("https://api.github.com")
        client(OkHttpClient())
    }

    dependencies {
        // Align versions of all Kotlin components
        implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.5.0"))

        // Use the Kotlin JDK 8 standard library.
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

        // Use the Kotlin test library.
        testImplementation("org.jetbrains.kotlin:kotlin-test")

        // Use the Kotlin JUnit integration.
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    }
}
