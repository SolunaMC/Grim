// Shared Fabric platform code for the intermediary + official variants (no NMS).
// Named "shared" (not "common") so its Gradle capability and nested-jar filename do not
// collide with the top-level cross-platform :common module.

plugins {
    `java-library`
    grim.`base-conventions`
}

repositories {
    exclusive("https://maven.fabricmc.net/") {
        includeGroup("net.fabricmc")
        includeGroup("net.fabricmc.fabric-api")
    }
    grimMaven()
    exclusive("https://jitpack.io", { mavenContent { releasesOnly() } }) {
        includeGroup("com.github.Fallen-Breath.conditional-mixin")
    }
    exclusive("https://nexus.scarsz.me/content/repositories/releases", { mavenContent { releasesOnly() } }) {
        includeGroup("github.scarsz")
    }
    mavenCentral()
}

dependencies {
    compileOnly(project(":common"))
    compileOnly(libs.grim.api)
    compileOnly(libs.grim.internal)
    compileOnly(libs.grim.internal.shims)

    compileOnly(libs.packetevents.api)

    compileOnly(libs.fabric.loader)

    compileOnly(libs.cloud.fabric)
    compileOnly(libs.luckperms)

    compileOnly(libs.snakeyaml)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.log4j.api)
    compileOnly(libs.jetbrains.annotations)
}
