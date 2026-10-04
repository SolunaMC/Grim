import versioning.BuildConfig

@Suppress("PropertyName")
val minecraft_version = property("minecraft_version") as String
@Suppress("PropertyName")
val fabric_version = property("fabric_version") as String

plugins {
    `maven-publish`
    // No version: loom is already on the classpath from the parent :fabric project,
    // so a version request here would fail compatibility checking.
    id("net.fabricmc.fabric-loom")
    grim.`base-conventions`
    grim.`jij-conventions`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

loom {
    accessWidenerPath = file("src/main/resources/grimac.accesswidener")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    compileOnly(libs.fabric.loader)

    implementation(libs.cloud.fabric.official) {
        exclude(group = "net.fabricmc.fabric-api")
    }
    implementation(libs.cloud.core)

    compileOnly(libs.fabric.permissions.api)
    implementation(fabricApi.module("fabric-lifecycle-events-v1", fabric_version))
    compileOnly("net.fabricmc.fabric-api:fabric-api:$fabric_version")

    implementation(project(":common"))
    implementation(project(":fabric:shared"))
    compileOnly(libs.packetevents.api)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.log4j.api)
}

allprojects {
    apply(plugin = "net.fabricmc.fabric-loom")
    apply(plugin = "grim.base-conventions")
    apply(plugin = "maven-publish")

    repositories {
        if (BuildConfig.mavenLocalOverride) mavenLocal()

        exclusive("https://maven.fabricmc.net/") {
            includeGroup("net.fabricmc")
            includeGroup("net.fabricmc.fabric-api")
        }

        grimMaven()

        exclusive("https://repo.viaversion.com", { mavenContent { releasesOnly() } }) {
            includeGroup("com.viaversion")
        }

        exclusive("https://nexus.scarsz.me/content/repositories/releases", { mavenContent { releasesOnly() } }) {
            includeGroup("github.scarsz")
        }

        exclusive("https://repo.opencollab.dev/maven-releases/", { mavenContent { releasesOnly() } }) {
            includeGroup("org.geysermc.api")
        }

        exclusive("https://repo.opencollab.dev/maven-snapshots/", { mavenContent { snapshotsOnly() } }) {
            includeGroup("org.geysermc.floodgate")
            includeGroup("org.geysermc.cumulus")
            includeModule("org.geysermc", "common")
            includeModule("org.geysermc", "geyser-parent")
        }

        mavenCentral()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    dependencies {
        val libsx = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
        compileOnly(libsx.findLibrary("fabric-loader").get())
    }

    publishing.publications.create<MavenPublication>("maven") {
        artifact(tasks["jar"])
    }

    tasks {
        matching { it.name == "sourcesJar" }
            .configureEach { enabled = false }

        jar {
            archiveBaseName = if (project == project(":fabric:official")) {
                "${rootProject.name}-fabric-official"
            } else {
                "${rootProject.name}-fabric-${project.name}"
            }
            archiveVersion = rootProject.version as String
        }
    }
}

subprojects {
    dependencies {
        implementation(project(":fabric:official"))
        compileOnly(project(":common"))
        compileOnly(project(":fabric:shared"))
        compileOnly(fabricApi.module("fabric-lifecycle-events-v1", fabric_version))
        val libsx = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
        compileOnly(libsx.findLibrary("packetevents-api").get())
    }
}

subprojects.forEach {
    dependencies {
        include(project(it.path))
    }
}
