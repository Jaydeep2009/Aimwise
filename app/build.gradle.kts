plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.jaydeep.aimwise"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jaydeep.aimwise"
        minSdk = 30
        targetSdk = 36
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation ("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    //navigation
    implementation("androidx.navigation:navigation-compose:2.8.0")

    //retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")


    // Firebase dependencies
    // Firebase BoM — manages versions for all Firebase libraries
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))


    // Firebase Authentication
    implementation( "com.google.firebase:firebase-auth")

    //Firebase google authentication
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Firebase Firestore (Cloud Firestore)
    implementation( "com.google.firebase:firebase-firestore")

    // Optional — Firebase Analytics (if you want analytics)
    implementation ("com.google.firebase:firebase-analytics")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")


    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    // Compose dependencies managed by BoM - duplicates removed
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.benchmark.common)
    implementation(libs.material)
    implementation(libs.androidx.navigation.safe.args.generator) {
        exclude(group = "xpp3", module = "xpp3")
        exclude(group = "xmlpull", module = "xmlpull")
    }
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.ui.graphics)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}