plugins {
    `kotlin-dsl`
    "groovy"
    "java-gradle-plugin"
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

repositories {
    maven {
        url = uri("https://plugins.gradle.org/m2/")
    }
    jcenter()
}

dependencies {
    api("com.netflix.nebula:gradle-ospackage-plugin:8.3.0")
    api("net.researchgate:gradle-release:2.6.0")
    api("com.github.jengelman.gradle.plugins:shadow:5.1.0")
    api("com.squareup.okhttp3:okhttp:3.14.1")
    api("com.fasterxml.jackson.core:jackson-databind:2.12.3")
    api("com.github.breadmoirai:github-release:2.2.12")
}
