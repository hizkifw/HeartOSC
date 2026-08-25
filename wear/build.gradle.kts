plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "red.kitsu.heartosc.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "red.kitsu.heartosc" // Must match the phone app ID to connect via Wearable API
        minSdk = 26
        targetSdk = 36
        // Keep Wear releases in a separate range from the phone APK.
        versionCode = 1_000_006
        versionName = "1.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.health.services.client)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation("com.google.guava:guava:31.1-android")
    testImplementation(libs.junit)
}
