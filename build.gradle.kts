plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // AGP 9 has built-in Kotlin. Applying the Compose compiler plugin at the pinned Kotlin version
    // also lifts KGP from AGP's default 2.2.10 to 2.2.21, the version LiteRT-LM 0.16.1 is built with.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
