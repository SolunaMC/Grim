@Suppress("PropertyName")
val minecraft_version = property("minecraft_version") as String

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
}
