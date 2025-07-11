plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.mediatoolkit"
    compileSdk = 35


    defaultConfig {
        applicationId = "com.example.mediatoolkit"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.2"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    viewBinding {
        enable = true
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.crashlytics.buildtools)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    //
    implementation(libs.okhttp)
    implementation(libs.converter.gson)
    implementation(libs.glide)
    implementation(libs.exoplayer)
    implementation(libs.androidx.cardview)
    implementation("com.google.android.gms:play-services-cast-framework:22.0.0")
    implementation(libs.play.services.cast)
    implementation(libs.androidx.mediarouter)
    implementation("jp.wasabeef:glide-transformations:4.3.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.exoplayer:extension-mediasession:2.19.1")
    implementation("com.google.android.exoplayer:extension-cast:2.19.1")
    implementation("androidx.media:media:1.7.0")
}
