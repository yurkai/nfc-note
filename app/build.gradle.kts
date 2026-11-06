plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.yurkai.nfcnote"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.yurkai.nfcnote"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    val keyPath = System.getenv("NFC_NOTE_KEYSTORE")
    signingConfigs {
        if (keyPath != null) create("release") {
            storeFile = file(keyPath)
            storePassword = System.getenv("NFC_NOTE_STORE_PASSWORD")
            keyAlias = System.getenv("NFC_NOTE_KEY_ALIAS")
            keyPassword = System.getenv("NFC_NOTE_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            if (keyPath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    testImplementation("junit:junit:4.13.2")
}
