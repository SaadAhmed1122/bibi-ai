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
    // The action router (S4, SPEC-tools.md). Only DecisionRouter.kt imports it. tasks-core pulls in
    // Google datatransport (usage logging, which would add INTERNET); DecisionMaker 1.2.0 never uses
    // it (S4: javap of the AAR, and the S4 app ran without it on the vivo), so it is excluded.
    implementation(libs.mediapipe.tasks.decision) {
        exclude(group = "com.google.android.datatransport")
    }
    implementation(project(":core:tools"))
    testImplementation(libs.junit)
}
