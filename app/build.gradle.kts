plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.weeddetectionapp"
    compileSdk = 36



    defaultConfig {
        applicationId = "com.example.weeddetectionapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
        }
    }

    // 核心修正：LiteRT 必须配合不压缩设置，才能在 16KB 设备运行
    packaging {
        jniLibs {
            useLegacyPackaging = false
//            useLegacyPackaging = false
        }
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    bundle {
        abi {
            enableSplit = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // --- 修复测试报错的核心依赖 ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // --- LiteRT 保持不变 ---
    val tfliteVersion = "2.16.1"
    implementation("org.tensorflow:tensorflow-lite:$tfliteVersion")
    implementation("org.tensorflow:tensorflow-lite-gpu:$tfliteVersion")
    implementation("org.tensorflow:tensorflow-lite-gpu-api:$tfliteVersion")

    // --- CameraX 保持不变 ---
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")


    implementation("androidx.exifinterface:exifinterface:1.3.6")


    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("androidx.cardview:cardview:1.0.0")
}