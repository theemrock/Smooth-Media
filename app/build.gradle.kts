import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.theemrock.smoothmedia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.theemrock.smoothmedia"
        minSdk = 33
        targetSdk = 35
        versionCode = 1002
        versionName = "v0.0.01.002"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    val kotlinxSerializationBom = platform(libs.kotlinx.serialization.bom)

    implementation(composeBom)
    implementation(kotlinxSerializationBom)
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.room.runtime)
    implementation(libs.sqlite.framework)

    ksp(libs.room.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.media3.exoplayer)
    androidTestImplementation(libs.room.testing)
}
