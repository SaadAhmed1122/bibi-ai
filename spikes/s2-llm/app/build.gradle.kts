plugins {
    id("com.android.application")
}

android {
    // Spike-only ID under the owner's GitHub Pages domain; the real app ID is still [OPEN].
    namespace = "io.github.saadahmed1122.bibi.spike.s2"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.saadahmed1122.bibi.spike.s2"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "s2-0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // LiteRT-LM Kotlin API (PRD §11.5 primary runtime), Google Maven.
    // Pinned to 0.16.1: per the LiteRT-LM Kotlin getting-started guide, 0.17.0–0.18.1 carry
    // Kotlin metadata 2.4.0 and need Kotlin ≥ 2.3, while AGP 9.0's built-in Kotlin is KGP 2.2.10
    // (AGP 9.0.0 release notes). 0.16.1 carries metadata 2.3.0 and builds with Kotlin 2.2.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
}
