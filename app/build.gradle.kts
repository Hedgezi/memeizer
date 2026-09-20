plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

import java.io.FileInputStream
import java.util.Properties

val signingPropertiesPath = providers.gradleProperty("memeizerSigningProperties")
    .orElse(providers.environmentVariable("MEMEIZER_SIGNING_PROPERTIES"))
    .orElse("${System.getProperty("user.home")}/.config/memeizer/signing.properties")
    .get()
val signingPropertiesFile = file(signingPropertiesPath)
val signingProperties = Properties()
if (signingPropertiesFile.isFile) {
    FileInputStream(signingPropertiesFile).use(signingProperties::load)
}
val hasReleaseSigning = signingPropertiesFile.isFile && listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
).all { !signingProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.darkesttrololo.memeizer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.darkesttrololo.memeizer"
        minSdk = 31
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 2
        versionName = "0.2.0"

        ndk {
            // The bundled native OCR/ML Kit libraries for 32-bit ABIs are
            // 4 KiB ELF-aligned; ship only the verified 16 KiB-capable ABIs.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

gradle.taskGraph.whenReady {
    val packagingRelease = allTasks.any { task ->
        task.name.contains("Release", ignoreCase = true) &&
            listOf("assemble", "bundle", "package", "install", "sign").any {
                task.name.startsWith(it, ignoreCase = true)
            }
    }
    if (packagingRelease && !hasReleaseSigning) {
        throw GradleException(
            "Release signing is not configured. Create $signingPropertiesPath with " +
                "storeFile, storePassword, keyAlias, and keyPassword, then rerun the release build.",
        )
    }
    if (packagingRelease && !signingPropertiesFile.isFile) {
        throw GradleException("Release signing properties file does not exist: $signingPropertiesPath")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    val roomVersion = "2.8.4"
    val lifecycleVersion = "2.10.0"

    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation(project(":ncnnAndroidPPOCR"))
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    ksp("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
