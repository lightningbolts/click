@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleSecrets)
    id("com.google.gms.google-services") version "4.4.2" apply false
}

val hasGoogleServicesConfig = listOf(
    file("google-services.json"),
    file("src/debug/google-services.json"),
    file("src/release/google-services.json")
).any { it.exists() }

if (hasGoogleServicesConfig) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("composeApp: google-services.json not found; skipping Google Services plugin for local builds")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            freeCompilerArgs += listOf("-Xbinary=bundleId=compose.project.click.click")
            export("com.mohamedrejeb.calf:calf-ui:0.9.0")
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation("androidx.core:core-ktx:1.17.0")
            implementation("io.livekit:livekit-android:2.20.3")
            // WebView-based map needs no native Map SDK dependency

            // Ktor Android engine
            implementation("io.ktor:ktor-client-android:3.0.1")

            // Security crypto for encrypted shared preferences
            implementation("androidx.security:security-crypto:1.1.0-alpha06")

            implementation("com.google.mlkit:barcode-scanning:17.2.0")
            implementation("androidx.camera:camera-camera2:1.3.1")
            implementation("androidx.camera:camera-lifecycle:1.3.1")
            implementation("androidx.camera:camera-view:1.3.1")
            implementation("com.google.accompanist:accompanist-permissions:0.34.0")

            // Google Maps
            implementation("com.google.maps.android:maps-compose:4.3.3")
            implementation("com.google.android.gms:play-services-maps:18.2.0")
            implementation("com.google.android.gms:play-services-location:21.0.1")
            implementation("com.google.firebase:firebase-messaging-ktx:23.4.1")

            implementation("androidx.work:work-runtime-ktx:2.10.0")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            api("com.mohamedrejeb.calf:calf-ui:0.9.0")

            // Supabase dependencies
            implementation(project.dependencies.platform("io.github.jan-tennert.supabase:bom:3.0.2"))
            implementation("io.github.jan-tennert.supabase:postgrest-kt")
            implementation("io.github.jan-tennert.supabase:auth-kt")
            implementation("io.github.jan-tennert.supabase:realtime-kt")
            implementation("io.github.jan-tennert.supabase:storage-kt")

            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")

            // Ktor client dependencies
            implementation("io.ktor:ktor-client-core:3.0.1")
            implementation("io.ktor:ktor-client-auth:3.0.1")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")

            // DateTime library – use the 0.6.x-compat artifact so Calf's transitive
            // kotlinx-datetime 0.7.1 upgrade doesn't remove Clock.System / Instant
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")

            // Kotlinx Serialization
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.krypto)

            implementation("io.github.g0dkar:qrcode-kotlin:4.1.1")

            // Multiplatform Settings for persistent session storage (explicit core artifact for SharedPreferencesSettings / NSUserDefaultsSettings)
            implementation("com.russhwolf:multiplatform-settings:1.2.0")
            implementation("com.russhwolf:multiplatform-settings-no-arg:1.2.0")
        }
        iosMain.dependencies {
            // Ktor iOS engine
            implementation("io.ktor:ktor-client-darwin:3.0.1")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        iosSimulatorArm64Test.dependencies {
            implementation(compose.uiTest)
        }
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.robolectric)
            implementation(libs.androidx.arch.core.testing)
            implementation(libs.androidx.test.core)
        }
    }
}

android {
    namespace = "compose.project.click.click"
    compileSdk = 36

    defaultConfig {
        applicationId = "compose.project.click.click"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "gumbo"
        // MapLibre doesn't require an API key
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}





