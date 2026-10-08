import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

// The draft-07 JSON Schemas for style definitions and avatar options (from @dicebear/schema),
// embedded as Kotlin source so validation works on every target.
val embedSchemas = tasks.register<EmbedJsonTask>("embedSchemas") {
    sourceDir = layout.projectDirectory.dir("schema")
    packageName = "xyz.felixb.dicebear.internal.schema"
    indexName = "schemaFiles"
    outputDir = layout.buildDirectory.dir("generated/schema/commonMain")
}

// The cross-language parity fixtures of the DiceBear monorepo (tests/fixtures/parity), embedded
// so the parity suite runs on every target, including the browser, wasm and the iOS simulator.
val embedParityFixtures = tasks.register<EmbedJsonTask>("embedParityFixtures") {
    sourceDir = layout.projectDirectory.dir("parity")
    packageName = "xyz.felixb.dicebear.parity"
    indexName = "parityFiles"
    outputDir = layout.buildDirectory.dir("generated/parity/commonTest")
}

// Avatars of every bundled style rendered by the official JS implementation
// (scripts/generate-reference.mjs), plus the bundled definitions they were rendered from.
val embedReferenceAvatars = tasks.register<EmbedJsonTask>("embedReferenceAvatars") {
    sourceDir = layout.projectDirectory.dir("reference")
    packageName = "xyz.felixb.dicebear.reference"
    indexName = "referenceFiles"
    outputDir = layout.buildDirectory.dir("generated/reference/commonTest")
}

val embedReferenceStyles = tasks.register<EmbedJsonTask>("embedReferenceStyles") {
    sourceDir = rootProject.layout.projectDirectory.dir("dicebear-styles/definitions")
    packageName = "xyz.felixb.dicebear.reference.styles"
    indexName = "referenceStyles"
    outputDir = layout.buildDirectory.dir("generated/referenceStyles/commonTest")
}

kotlin {
    explicitApi()

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }

    android {
        namespace = "xyz.felixb.dicebear.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        withHostTestBuilder {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    js {
        browser()
        // The reference suite renders hundreds of avatars in one test.
        nodejs { testTask { useMocha { timeout = "60s" } } }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs { testTask { useMocha { timeout = "60s" } } }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(embedSchemas)
        }
        commonTest {
            kotlin.srcDir(embedParityFixtures)
            kotlin.srcDir(embedReferenceAvatars)
            kotlin.srcDir(embedReferenceStyles)
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = "DiceBear Kotlin core"
            description = "Unofficial Kotlin Multiplatform port of the DiceBear avatar engine " +
                "(not affiliated with DiceBear). Renders deterministic SVG avatars offline."
            licenses {
                license {
                    name = "MIT"
                    url = "https://opensource.org/license/mit"
                }
            }
        }
    }
}

// Ship the MIT notice (DiceBear's copyright) inside every jar.
tasks.withType<Jar>().configureEach {
    metaInf {
        from(rootProject.file("LICENSE"))
    }
}

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
