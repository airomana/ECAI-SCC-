plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.eldercare.ai"
    compileSdk = 34
    
    // 指定NDK版本（使用已安装的版本）
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.eldercare.ai"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        
        // 启用原生代码编译（Whisper语音识别）
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-frtti", "-fexceptions", "-Wno-format")
                arguments += listOf(
                    "-DANDROID_PLATFORM=android-24",
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=TRUE",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
                )
            }
        }
    }

    buildTypes {
        debug {
            // 确保使用debug签名
            signingConfig = signingConfigs.getByName("debug")
        }
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
    
    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    buildFeatures {
        compose = true
        viewBinding = true
        dataBinding = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES.txt"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/dependencies.txt"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/notice.txt"
            excludes += "LICENSE.txt"
            excludes += "NOTICE"
            excludes += "META-INF/ASL2.0"
            excludes += "DB_VERSION.txt"
            excludes += "META-INF/rxjava.properties"
            excludes += "META-INF/atomicfu.kotlin_module"
        }
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "**/libc++_shared.so"
            pickFirsts += "**/libjsc.so"
        }
    }
    
    // 防止.bin、PaddleOCR 模型文件被压缩
    androidResources {
        noCompress += listOf("bin", "param", "pdmodel", "pdiparams")
    }
    
    // 启用原生代码编译配置（Whisper语音识别）
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDir(file("../../documents"))
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    
    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation("androidx.compose.runtime:runtime-livedata:1.5.1")
    
    // Room（使用 KSP 替代 kapt）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Gson for JSON serialization
    implementation(libs.gson)

    // PaddleOCR（端侧文字识别，基于 equationl/paddleocr4android FastDeploy）
    implementation("com.github.equationl.paddleocr4android:fastdeplyocr:v1.2.9")
    
    // Retrofit & OkHttp (for LLM API)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
