import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named

plugins {
    id("java")
    id("idea")
    id("multiloader-common")
    id("maven-publish")
    id("com.gradleup.shadow")
//    id("net.xmx.velthoric.publishing")
}

val bundled by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly(project(":common"))
    compileOnly(project(":vx-events"))
    compileOnly(project(":vx-native"))

    bundled(project(":common"))
    bundled(project(":vx-events"))
    bundled(project(":vx-native"))
}

tasks {
    jar {
        exclude("accesswideners/**")
    }

    shadowJar {
        dependsOn(tasks.jar)
        configurations = listOf(bundled)
        archiveClassifier.set("all")
        destinationDirectory.set(layout.buildDirectory.dir("libs").get().asFile)

        exclude("aix/**")
        exclude("darwin/**")
        exclude("freebsd/**")
        exclude("linux/**")
        exclude("win/**")
    }

//    named<Jar>("sourcesJar") {
//        val commonSources = project(":common").extensions.getByType(SourceSetContainer::class.java)["main"].allSource
//        val vxEventsSources = project(":vx-events").extensions.getByType(SourceSetContainer::class.java)["main"].allSource
//        val vxNativeSources = project(":vx-native").extensions.getByType(SourceSetContainer::class.java)["main"].allSource
//
//        from(commonSources)
//        from(vxEventsSources)
//        from(vxNativeSources)
//        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//    }
}

//extensions.configure<PublishingExtension>("publishing") {
//    publications {
//        create<MavenPublication>("mavenJava") {
//            artifactId = project.name
//
//            artifact(tasks.named("remapJar"))
//            artifact(tasks.named("remapSourcesJar"))
//        }
//    }
//}

//extensions.configure<VelthoricPublishingExtension>("velthoricPublishing") {
//    loader.set(requireNotNull(prop("loader")))
//    displayName.set(requireNotNull(prop("loader_name")))
//    artifact.set(tasks.named("remapJar"))
//    dryRun.set(false)
//}