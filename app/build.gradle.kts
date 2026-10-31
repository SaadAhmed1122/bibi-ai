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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
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
