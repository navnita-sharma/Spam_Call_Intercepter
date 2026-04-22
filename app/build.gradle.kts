plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }

    // Required: allow Vosk's large .so native libs to be packaged correctly
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    androidResources {
        noCompress.addAll(listOf("model-en-us", "tflite"))
    }
}

configurations.all {
    exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    exclude(group = "org.tensorflow", module = "tensorflow-lite-support-api")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.icons.extended)

    // Coroutines (for background STT work)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // ── Part 1: Vosk Offline Speech-to-Text ──────────────────────────────────
    // Official Maven Central release (no custom repo needed)
    implementation("com.alphacephei:vosk-android:0.3.75")

    // ── Part 2: TensorFlow Lite for Local NLP ────────────────────────────────
    implementation("org.tensorflow:tensorflow-lite-task-text:0.4.4")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

