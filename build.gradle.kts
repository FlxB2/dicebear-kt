plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.mavenPublish) apply false
}

// This project's own version. The ported DiceBear versions are listed in the README.
allprojects {
    group = "xyz.felixb.dicebear"
    version = "0.1.0"
}

// POM fields shared by every published module; name, description and licenses are per module.
subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    url = "https://github.com/FlxB2/dicebear-kt"
                    developers {
                        developer {
                            id = "FlxB2"
                            url = "https://github.com/FlxB2"
                        }
                    }
                    scm {
                        url = "https://github.com/FlxB2/dicebear-kt"
                        connection = "scm:git:https://github.com/FlxB2/dicebear-kt.git"
                        developerConnection = "scm:git:ssh://git@github.com/FlxB2/dicebear-kt.git"
                    }
                }
            }
        }
    }
}
