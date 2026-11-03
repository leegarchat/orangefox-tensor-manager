plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.orangefox.tensor.manager"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.orangefox.tensor.manager"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Release key lives OUTSIDE the repo (~/.android-keys/).
            // Passwords come from env or ~/.gradle/gradle.properties:
            // OFOX_STORE_PASSWORD / OFOX_KEY_PASSWORD (+ OFOX_KEY_ALIAS).
            // Unsigned release if absent (CI signs separately).
            val ks = (System.getenv("OFOX_KEYSTORE")
                ?: findProperty("OFOX_KEYSTORE")?.toString()
                ?: "${System.getProperty("user.home")}/.android-keys/pixelofox-release.jks")
            if (file(ks).exists()) {
                val sp = System.getenv("OFOX_STORE_PASSWORD")
                    ?: findProperty("OFOX_STORE_PASSWORD")?.toString()
                // PKCS12 stores ignore a separate key password: use the store one.
                storeFile = file(ks)
                storePassword = sp
                keyAlias = System.getenv("OFOX_KEY_ALIAS")
                    ?: findProperty("OFOX_KEY_ALIAS")?.toString() ?: "pixelofox"
                keyPassword = sp
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
}
