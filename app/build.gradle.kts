import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    val inputStream = localPropertiesFile.inputStream()
    localProperties.load(inputStream)
    inputStream.close()
}
val cloudinaryApiKey = localProperties.getProperty("CLOUDINARY_API_KEY") ?: ""
val cloudinaryApiSecret = localProperties.getProperty("CLOUDINARY_API_SECRET") ?: ""

android {
    namespace = "com.splitsmith.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.splitsmith.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 48
        versionName = "0.3.8.5"

        buildConfigField("String", "CLOUDINARY_API_KEY", "\"$cloudinaryApiKey\"")
        buildConfigField("String", "CLOUDINARY_API_SECRET", "\"$cloudinaryApiSecret\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("../splitsmith-upload-key.jks")
            storePassword = "splitsmithpassword123"
            keyAlias = "splitsmith-upload"
            keyPassword = "splitsmithpassword123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Firebase
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.storage)
  implementation(libs.firebase.ai)
  implementation("com.google.firebase:firebase-messaging")
  implementation(libs.play.services.auth)
  implementation(libs.androidx.material.icons.core)
  implementation("androidx.compose.material:material-icons-extended")
  implementation(libs.androidx.google.fonts)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation("androidx.biometric:biometric:1.1.0")

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // QR Code generation & scanning
  implementation("com.journeyapps:zxing-android-embedded:4.3.0")
  implementation("com.google.zxing:core:3.5.2")

  // Coil Image Loading
  implementation("io.coil-kt:coil-compose:2.6.0")

  // Google Generative AI SDK for Gemini AI Slip Extraction
  implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

  // Google ML Kit on-device Text Recognition for offline fallback OCR
  implementation("com.google.mlkit:text-recognition:16.0.1")

  // Cloudinary SDK for Receipt Uploads
  implementation("com.cloudinary:cloudinary-android:2.5.0")

  // Android WorkManager
  implementation("androidx.work:work-runtime-ktx:2.9.0")
}



