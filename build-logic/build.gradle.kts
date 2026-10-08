plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        register("embedJson") {
            id = "dicebear.embed-json"
            implementationClass = "EmbedJsonPlugin"
        }
    }
}
