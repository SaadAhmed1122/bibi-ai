import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.saadahmed1122.bibi"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.saadahmed1122.bibi"
        minSdk = 32
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.4-p3"
        // 64-bit ARM only: the test phones (vivo Y17s, Galaxy A23) are arm64, and the native AI
        // libraries (LiteRT-LM, MediaPipe, sherpa-onnx) otherwise add three more ABIs (APK 359 MB).
        ndk { abiFilters += "arm64-v8a" }
    }

    // Upload key from ~/.gradle/gradle.properties (docs/release.md); never in the repo. Without
    // all four properties the release build is unsigned, and the owner signs it.
    val uploadKey = listOf(
        "BIBI_UPLOAD_STORE_FILE", "BIBI_UPLOAD_STORE_PASSWORD", "BIBI_UPLOAD_KEY_ALIAS", "BIBI_UPLOAD_KEY_PASSWORD",
    ).associateWith { providers.gradleProperty(it).orNull }
    val hasUploadKey = uploadKey.values.all { !it.isNullOrBlank() }
    if (hasUploadKey) {
        signingConfigs.create("upload") {
            storeFile = file(uploadKey.getValue("BIBI_UPLOAD_STORE_FILE")!!)
            storePassword = uploadKey.getValue("BIBI_UPLOAD_STORE_PASSWORD")
            keyAlias = uploadKey.getValue("BIBI_UPLOAD_KEY_ALIAS")
            keyPassword = uploadKey.getValue("BIBI_UPLOAD_KEY_PASSWORD")
        }
    } else {
        logger.warn("Bibi: BIBI_UPLOAD_* properties not set; the release build will be unsigned (docs/release.md).")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasUploadKey) signingConfig = signingConfigs.getByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Where the in-app downloads fetch the models (SPEC-model-download.md). Set BIBI_MODEL_BASE_URL in
    // ~/.gradle/gradle.properties or the environment to the owner's server folder, which holds
    // gemma3-1b-it-int4.litertlm, embeddinggemma-2-text-270m.litertlm and voice/<folder>/<file>.
    // Without it: the chat model is the gated Hugging Face file (answers 401; testing only), the
    // action model its public Hugging Face file, and there is no voice download (empty base).
    val base = providers.gradleProperty("BIBI_MODEL_BASE_URL").orElse(providers.environmentVariable("BIBI_MODEL_BASE_URL"))
        .orNull?.trimEnd('/')
    val hf = "https://huggingface.co/litert-community"
    defaultConfig.buildConfigField("String", "MODEL_URL", "\"${base?.let { "$it/gemma3-1b-it-int4.litertlm" } ?: "$hf/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm"}\"")
    defaultConfig.buildConfigField("String", "ROUTER_URL", "\"${base?.let { "$it/embeddinggemma-2-text-270m.litertlm" } ?: "$hf/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm"}\"")
    defaultConfig.buildConfigField("String", "VOICE_BASE_URL", "\"${base?.let { "$it/voice" }.orEmpty()}\"")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":ai:inference-litert"))
    implementation(project(":core:database"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:tools"))
    implementation(project(":feature:memory"))
    implementation(libs.androidx.work.runtime)
    implementation(project(":companion:overlay"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Fails the build if the merged manifest asks for a permission not on [allowed]: a library can't add
 * one unnoticed (PRD 0.9). INTERNET is only for the model download (SPEC-model-download.md).
 */
abstract class PermissionAllowlistCheck : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val allowed: SetProperty<String>

    @TaskAction
    fun check() {
        val asked = Regex("""<uses-permission[^>]*android:name="([^"]+)"""")
            .findAll(manifest.get().asFile.readText()).map { it.groupValues[1] }.toSet()
        val extra = asked.filterNot { it in allowed.get() || it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
        if (extra.isNotEmpty()) throw GradleException("Permissions not on the allowlist: $extra (${manifest.get().asFile})")
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register<PermissionAllowlistCheck>("check${name}Permissions") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            allowed.set(
                listOf(
                    "INTERNET", "RECORD_AUDIO", "POST_NOTIFICATIONS", "SYSTEM_ALERT_WINDOW", "WAKE_LOCK",
                    "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MICROPHONE", "FOREGROUND_SERVICE_SPECIAL_USE",
                    "RECEIVE_BOOT_COMPLETED",
                ).map { "android.permission.$it" } + "com.android.alarm.permission.SET_ALARM",
            )
        }
        tasks.matching { it.name == "assemble$name" || it.name == "bundle$name" || it.name == "check" }
            .configureEach { dependsOn(check) }
    }
}
