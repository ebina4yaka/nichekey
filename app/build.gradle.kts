plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.example.jpkeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.example.jpkeyboard"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("io.github.greattusk:wanakana-common-android:1.0.1")
    testImplementation("junit:junit:4.13.2")
}
