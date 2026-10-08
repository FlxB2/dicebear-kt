import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    id("dicebear.embed-json")
    `maven-publish`
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

tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
