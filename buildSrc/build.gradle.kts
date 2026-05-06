import java.util.Properties

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.kikugie.dev/snapshots")
}

val rootProps = Properties().apply {
    rootDir.parentFile.resolve("gradle.properties").inputStream().use(::load)
}
val stonecutterVersion: String = rootProps.getProperty("stonecutter.version")

dependencies {
    fun plugin(id: String, version: String) = "$id:$id.gradle.plugin:$version"
    implementation("dev.kikugie:stonecutter:$stonecutterVersion")
    implementation("me.modmuss50:mod-publish-plugin:0.8.4")
    implementation("com.github.breadmoirai:github-release:2.4.1")

    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.4.1")
}

gradlePlugin {
    plugins {
        create("velthoricPublishing") {
            id = "net.xmx.velthoric.publishing"
            implementationClass = "net.xmx.velthoric.gradle.VelthoricPublishingPlugin"
        }
    }
}