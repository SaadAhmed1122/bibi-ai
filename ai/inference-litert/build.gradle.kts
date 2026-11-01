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

/**
 * sherpa-onnx (voice, SPEC-voice.md) isn't on Maven or Google Maven; it ships as an AAR on its GitHub
 * release. Download sherpa-onnx-2.0.1.aar once into the repo's libs/ (git-ignored), the same file the
 * S3 spike measured: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1
 */
val sherpaAar = rootProject.file("libs/sherpa-onnx-2.0.1.aar")
if (!sherpaAar.exists()) {
    throw GradleException("Missing ${sherpaAar.path}: download sherpa-onnx-2.0.1.aar from https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1 into libs/")
}

dependencies {
    implementation(files(sherpaAar))
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
