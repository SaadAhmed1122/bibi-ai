plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.saadahmed1122.bibi.core.common"
    compileSdk = 36
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
}
