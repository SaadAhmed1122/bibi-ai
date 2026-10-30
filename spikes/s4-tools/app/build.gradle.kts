plugins {
    id("com.android.application")
}

android {
    // Spike-only ID under the owner's GitHub Pages domain; the real app ID is still [OPEN].
    namespace = "io.github.saadahmed1122.bibi.spike.s4"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.saadahmed1122.bibi.spike.s4"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "s4-0.2"
        // The models are pushed to the external files dir, never bundled (README → Models).
        ndk {
            // Both runtimes ship arm64-v8a; the test phones are arm64. Keeps the APK smaller.
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

// Spike-only dependencies for the real-LLM tool-call path (Decision 2026-10-30 in
// docs/project_status.md; overrides SPEC.md §4 "no libraries").
dependencies {
    // LiteRT-LM Kotlin API, the same pin as S2 (spikes/s2-llm/app/build.gradle.kts): 0.17.0+ needs
    // Kotlin ≥ 2.3, AGP 9.0's built-in Kotlin is 2.2. Do not upgrade here.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    // MediaPipe Decision Maker (Google Maven, AAR minSdk 24). tasks-core 1.2.0 pulls in Google
    // datatransport (usage logging), which merges INTERNET and ACCESS_NETWORK_STATE. Only
    // tasks-core's RemoteLoggingClient uses it, created by TasksStatsProtoLogger from TaskRunner;
    // DecisionMaker 1.2.0 never uses TaskRunner (javap of the AAR; strings of both JNI libs show no
    // logging classes). So it is excluded, and the manifest removes both permissions as well.
    // That this holds at runtime is [VERIFY on device].
    implementation("com.google.mediapipe:tasks-decision:1.2.0") {
        exclude(group = "com.google.android.datatransport")
    }
    // Same version LiteRT-LM 0.16.1 already brings in (its POM); declared because the app uses it.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
