plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A versão mora em gradle.properties; o versionCode sai dela (1.2.3 -> 10203).
val semver = version.toString()
val code = semver.split(".").map(String::toInt).let { (a, b, c) -> a * 10000 + b * 100 + c }

// Sem MINDBLOW_KEYSTORE, o release sai assinado com a chave de debug, só para testar.
val keystore: String? = System.getenv("MINDBLOW_KEYSTORE")

android {
    namespace = "app.mindblow.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.mindblow.android"
        minSdk = 28
        targetSdk = 35
        versionCode = code
        versionName = semver
    }

    signingConfigs {
        create("release") {
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("MINDBLOW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MINDBLOW_KEY_ALIAS")
                keyPassword = System.getenv("MINDBLOW_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
        }
        // Versão de teste das branches: instala ao lado do app normal, como "mindblow teste".
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".teste"
            versionNameSuffix = "-teste"
            matchingFallbacks += "release"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
