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
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-p1"
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
}

/** Fails the build if a merged manifest asks for INTERNET: Bibi is offline (SPEC.md Boundaries). */
abstract class NoInternetPermissionCheck : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun check() {
        if (manifest.get().asFile.readText().contains("android.permission.INTERNET")) {
            throw GradleException("Merged manifest requests android.permission.INTERNET: ${manifest.get().asFile}")
        }
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register<NoInternetPermissionCheck>("check${name}NoInternet") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        tasks.matching { it.name == "assemble$name" || it.name == "bundle$name" || it.name == "check" }
            .configureEach { dependsOn(check) }
    }
}
