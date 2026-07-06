plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.code2hack.scopex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.code2hack.scopex"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":scopex-core"))
    testImplementation(kotlin("test-junit"))
}
