dependencies {
    minecraft("com.mojang:minecraft:1.19.4")
    mappings(loom.officialMojangMappings())
    compileOnly(project(":fabric:intermediary:mc1161", configuration = "namedElements"))
    compileOnly(project(":fabric:intermediary:mc1171", configuration = "namedElements"))

    modImplementation(fabricApi.module("fabric-lifecycle-events-v1", "0.87.2+1.19.4"))
    modCompileOnly(libs.fabric.permissions.api)
}

tasks.compileJava {
    options.release.set(17)
}
