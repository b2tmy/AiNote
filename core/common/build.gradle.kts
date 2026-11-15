plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ainote.core.common"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)

    // 中文 OCR 模型打包进 APK（约 +15MB），换取完全离线可用
    implementation(libs.mlkit.text.recognition.chinese)
}
