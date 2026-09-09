plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    config.from(rootProject.file("detekt.yml"))
    baseline = file("detekt-baseline.xml")
    parallel = true
    reports.html.required = true
    reports.xml.required = true
    reports.sarif.required = true
}

android {
    namespace = "dev.example.jpkeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.example.jpkeyboard"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "0.2.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("io.github.greattusk:wanakana-common-android:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    testImplementation("junit:junit:4.13.2")
}
