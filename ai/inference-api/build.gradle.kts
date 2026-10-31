plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.saadahmed1122.bibi.ai.inference.api"
    compileSdk = 36
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // StateFlow and Flow are part of the public API.
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
