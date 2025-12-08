plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.breastscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.breastscan"
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

    // Required for TensorFlow Lite
    aaptOptions {
        noCompress += listOf("tflite")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    // AndroidX + UI
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ✔ KEEP ONLY ONE MATERIAL VERSION (LATEST)
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.12.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Image Libraries
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.squareup.picasso:picasso:2.8")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation ("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.15.1")





// OkHttp and Gson (if not already present)
    implementation ("com.squareup.okhttp3:okhttp:4.11.0")
    implementation ("com.google.code.gson:gson:2.10.1")


}
