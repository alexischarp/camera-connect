// Top-level build file: plugin versions come from gradle/libs.versions.toml.
// AGP 9 compiles Kotlin itself (built-in Kotlin); this raises the Kotlin Gradle plugin it uses.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
