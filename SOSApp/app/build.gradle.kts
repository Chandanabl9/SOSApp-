plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sosapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sosapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.camera.core.ExperimentalGetImage")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += listOf("tflite", "task")
    }

    // Prevent META-INF conflicts from Vosk + JNA
    packaging {
        resources {
            excludes += listOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Compose
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.7.3")
    implementation("androidx.compose.foundation:foundation:1.6.0")

    // Location & DataStore
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.3")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-task-audio:0.4.3")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.13.0")
    // MediaPipe
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // ML Kit Face Detection
    //implementation("com.google.mlkit:face-detection:17.1.2")
    //implementation("com.google.mlkit:face-detection:17.1.2")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // TarsosDSP (local JAR)
    implementation(files("libs/tarsosdsp.jar"))

    // Vosk + JNA
    implementation("com.alphacephei:vosk-android:0.3.38")
    //implementation("net.java.dev.jna:jna:5.13.0")
    //implementation("net.java.dev.jna:jna-platform:5.13.0")

    // Material & AppCompat
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // MP3agic
    implementation("com.mpatric:mp3agic:0.9.1")
}
