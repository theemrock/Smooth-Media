import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
        versionCode = 1008
        versionName = "v0.0.01.008"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testBuildType = "p09test"

    signingConfigs {
        create("smoothRelease") {
            val signingKeys = listOf(
                "SMOOTH_SIGNING_STORE_FILE",
                "SMOOTH_SIGNING_STORE_PASSWORD",
                "SMOOTH_SIGNING_KEY_ALIAS",
                "SMOOTH_SIGNING_KEY_PASSWORD",
            )
            val signingValues = signingKeys.associateWith { providers.environmentVariable(it).orNull }
            val configured = signingValues.values.any { !it.isNullOrBlank() }
            if (configured) {
                signingKeys.forEach { name ->
                    require(!signingValues[name].isNullOrBlank()) { "Missing signing environment variable: $name" }
                }
                val path = signingValues.getValue("SMOOTH_SIGNING_STORE_FILE")!!
                require(file(path).isFile) { "Release keystore file not found" }
                storeFile = file(path)
                storePassword = signingValues.getValue("SMOOTH_SIGNING_STORE_PASSWORD")
                keyAlias = signingValues.getValue("SMOOTH_SIGNING_KEY_ALIAS")
                keyPassword = signingValues.getValue("SMOOTH_SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        create("p09test") {
            applicationIdSuffix = ".p09test"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("smoothRelease")
        }
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

tasks.register("compileDebugAndroidTestKotlin") {
    description = "Compiles the isolated p09test Android test Kotlin sources."
    dependsOn("compileP09testAndroidTestKotlin")
}

tasks.withType<KotlinCompile>().configureEach {
    if (name == "compileP09testAndroidTestKotlin") {
        doFirst {
            setSource(fileTree("src/androidTest") {
                include("**/*.kt")
                exclude("**/p01x/**")
            })
        }
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
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    ksp(libs.room.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.media3.exoplayer)
    androidTestImplementation(libs.room.testing)
}
