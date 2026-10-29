plugins {
    id("com.android.application")
}

// sherpa-onnx is not on Maven Central or Google Maven; it ships as an AAR on its GitHub release
// page. The owner downloads it once into app/libs/ (README.md, "Get the sherpa-onnx library").
// Local AAR dependency syntax: developer.android.com/studio/projects/android-library
// ("Add your AAR or JAR as a dependency").
val sherpaVersion = "2.0.1"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
if (!sherpaAar.exists()) {
    throw GradleException(
        "Missing ${sherpaAar.path}. Download sherpa-onnx-$sherpaVersion.aar from " +
            "https://github.com/k2-fsa/sherpa-onnx/releases/tag/v$sherpaVersion and put it in " +
            "spikes/s3-voice/app/libs/ (see spikes/s3-voice/README.md).",
    )
}

android {
    // Spike-only ID under the owner's GitHub Pages domain; the real app ID is still [OPEN].
    namespace = "io.github.saadahmed1122.bibi.spike.s3"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.saadahmed1122.bibi.spike.s3"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "s3-0.1"

        // Both test phones (vivo Y17s, Galaxy A23) are arm64. The AAR carries four ABIs;
        // packaging only arm64 keeps the APK small. Ndk.abiFilters: AGP 9.0 DSL reference.
        ndk {
            abiFilters += "arm64-v8a"
        }
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
    implementation(files(sherpaAar))
}
