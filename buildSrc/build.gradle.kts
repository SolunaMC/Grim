plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlin.serialization) // Use the same Kotlin version as your main project
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spotless)
    implementation(libs.lombok)
    implementation(libs.shadow)
    implementation(libs.kotlinx.serialization.json)
}
