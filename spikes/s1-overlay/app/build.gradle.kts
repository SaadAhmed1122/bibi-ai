plugins {
    id("com.android.application")
}

android {
    // Spike-only ID under the owner's GitHub Pages domain; the real app ID is still [OPEN].
    namespace = "io.github.saadahmed1122.bibi.spike.s1"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.saadahmed1122.bibi.spike.s1"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "s1-0.1"
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
