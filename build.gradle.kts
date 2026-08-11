plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.googleSecrets) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // Only enforce style on lines changed since main — avoids a repo-wide reformat.
    ratchetFrom("origin/main")
    kotlin {
        target(
            "composeApp/src/**/*.kt",
            "composeApp/*.kt",
        )
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "composeApp/*.gradle.kts", "gradle/*.gradle.kts")
        ktlint()
    }
}
