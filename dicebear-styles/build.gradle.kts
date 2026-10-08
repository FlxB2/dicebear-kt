import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    id("dicebear.embed-json")
    alias(libs.plugins.mavenPublish)
}

// Publishing to Maven Central: see "Publishing" in the README.
mavenPublishing {
    publishToMavenCentral()
    // Only sign when a key is configured, so publishToMavenLocal works without one.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}

// Every style definition of the @dicebear/styles release (dist/*.min.json, see README for the
// version), embedded as Kotlin source with one public `DiceBearStyles.<name>` accessor per style.
val embedStyles = tasks.register<EmbedJsonTask>("embedStyles") {
    sourceDir = layout.projectDirectory.dir("definitions")
    packageName = "xyz.felixb.dicebear.styles"
    indexName = "styleDefinitions"
    styleAccessors = true
    outputDir = layout.buildDirectory.dir("generated/styles/commonMain")
}

kotlin {
    explicitApi()

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }

    android {
        namespace = "xyz.felixb.dicebear.styles"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        withHostTestBuilder {}
    }

    // `./gradlew :dicebear-styles:assembleDiceBearXCFramework` builds DiceBear.xcframework for
    // Swift, exporting the core API (Avatar, Style, …) next to the bundled styles.
    val xcFramework = XCFramework("DiceBear")
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "DiceBear"
            export(project(":dicebear-core"))
            xcFramework.add(this)
        }
    }

    js {
        browser()
        // Parsing and rendering all 63 styles in one test exceeds mocha's 2s default.
        nodejs { testTask { useMocha { timeout = "60s" } } }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs { testTask { useMocha { timeout = "60s" } } }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(embedStyles)
            dependencies {
                api(project(":dicebear-core"))
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = "DiceBear Kotlin styles"
            description = "The 63 DiceBear avatar styles for the unofficial DiceBear Kotlin port " +
                "(not affiliated with DiceBear). Each style has its own license, see LICENSE-STYLES.md."
            licenses {
                license {
                    name = "MIT"
                    url = "https://opensource.org/license/mit"
                    comments = "Code and the Icons style"
                }
                license {
                    name = "CC0-1.0"
                    url = "https://creativecommons.org/publicdomain/zero/1.0/"
                    comments = "44 styles"
                }
                license {
                    name = "CC-BY-4.0"
                    url = "https://creativecommons.org/licenses/by/4.0/"
                    comments = "14 styles, attribution required"
                }
                license {
                    name = "Free for personal and commercial use"
                    url = "https://github.com/FlxB2/dicebear-kt/blob/main/dicebear-styles/LICENSE.md"
                    comments = "4 styles (Avataaars, Bottts); per-style details in LICENSE-STYLES.md"
                }
            }
        }
    }
}

// Ship the per-style licenses (and the MIT notice for the code) inside every jar.
tasks.withType<Jar>().configureEach {
    metaInf {
        from(rootProject.file("LICENSE"))
        from(file("LICENSE.md")) { rename { "LICENSE-STYLES.md" } }
    }
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
