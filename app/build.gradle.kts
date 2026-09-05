plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dpoza.sunmiprinterserver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dpoza.sunmiprinterserver"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Lightweight embedded HTTP server.
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // SDK oficial de impresora Sunmi (trae el AIDL correcto para el firmware del terminal).
    // https://mvnrepository.com/artifact/com.sunmi/printerlibrary
    implementation("com.sunmi:printerlibrary:1.0.24")
}
