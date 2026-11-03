import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The signing key and its passwords stay outside the project folder; see README.
val signingFile = File(System.getProperty("user.home"), ".ravager/keystore.properties")
val signing = Properties().apply { if (signingFile.exists()) signingFile.inputStream().use { load(it) } }

android {
    namespace = "com.loop.explorer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.loop.explorer"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "UPDATE_REPO", "\"${providers.gradleProperty("loop.updateRepo").orNull.orEmpty()}\"")
        buildConfigField("String", "UPDATE_TAG_PREFIX", "\"${providers.gradleProperty("loop.updateTag").orNull.orEmpty()}\"")
    }

    signingConfigs {
        if (signingFile.exists()) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
