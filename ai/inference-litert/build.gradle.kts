plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.saadahmed1122.bibi.ai.inference.litert"
    compileSdk = 36
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":ai:inference-api"))
    // Pinned to 0.16.1 (libs.versions.toml). Only LiteRtLmEngine.kt imports it.
    implementation(libs.litertlm.android)
    testImplementation(libs.junit)
}
