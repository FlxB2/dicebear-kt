plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
}

allprojects {
    group = "xyz.felixb.dicebear"
    version = "11.0.0-rc.2"
}
