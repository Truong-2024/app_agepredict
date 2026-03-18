plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.app_agepredict"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.app_agepredict"
        minSdk = 26
        targetSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    // --- Các thư viện mặc định ---
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // --- SDK nội bộ ---
    implementation(project(":sdk"))

    // --- TENSORFLOW LITE (Đã sửa lỗi phiên bản) ---
    // Thư viện Core dùng đầu số 2.x
    implementation("org.tensorflow:tensorflow-lite:2.14.0")

    // Thư viện Task và Support dùng đầu số 0.4.x (Đây là nơi gây ra 9 lỗi của bạn)
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-metadata:0.4.4")

    // --- CAMERA X & ML KIT ---
    implementation("androidx.camera:camera-camera2:1.4.0")
    implementation("androidx.camera:camera-lifecycle:1.4.0")
    implementation("androidx.camera:camera-view:1.4.0")
    implementation("com.google.mlkit:face-detection:16.1.5")
}

// Chống xung đột phiên bản từ các thư viện con (Cực kỳ quan trọng để dứt điểm lỗi)
configurations.all {
    resolutionStrategy {
        force("org.tensorflow:tensorflow-lite-task-vision:0.4.4")
        force("org.tensorflow:tensorflow-lite-support:0.4.4")
    }
}