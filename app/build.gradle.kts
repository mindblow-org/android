plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A versão mora em gradle.properties; o versionCode sai dela (1.2.3 -> 10203).
val semver = version.toString()
val code = semver.split(".").map(String::toInt).let { (a, b, c) -> a * 10000 + b * 100 + c }

// Sem MINDBLOW_KEYSTORE, o release sai assinado com a chave de debug, só para testar.
val keystore: String? = System.getenv("MINDBLOW_KEYSTORE")

// O servidor do mindblow vem do repo do desktop (mindblow-org/desktop), compilado para
// Android como libmindblow.so. MINDBLOW_SRC aponta para ele; por padrão, ../mindblow.app.
val src = file(System.getenv("MINDBLOW_SRC") ?: rootDir.resolve("../mindblow.app"))
val jni = layout.buildDirectory.dir("jni")
// Só para mexer no Kotlin sem o Rust para Android: o APK sai sem o servidor e não abre.
val skipNode = System.getenv("MINDBLOW_SKIP_NODE") != null

android {
    namespace = "app.mindblow.android"
    compileSdk = 35
    ndkVersion = "27.1.12297006"

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
            // Celular é arm64; o x86_64 (5 MB do servidor) só serve no emulador, com o debug.
            ndk { abiFilters += "arm64-v8a" }
        }
        // Teste do servidor junto e do sync: instala ao lado do app normal, como "mindblow offline".
        create("offline") {
            initWith(getByName("release"))
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
            matchingFallbacks += "release"
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

    sourceSets["main"].jniLibs.srcDir(jni)

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    jvmToolchain(17)
}

// A interface vai embutida no servidor, então ela compila antes.
val web = tasks.register<Exec>("web") {
    workingDir = src.resolve("web")
    commandLine("sh", "-c", "pnpm install --frozen-lockfile && pnpm build")
    onlyIf { !skipNode }
}

// cargo-ndk compila o crate `mobile` para cada ABI e põe os .so em build/jni/<abi>/.
val node = tasks.register<Exec>("node") {
    dependsOn(web)
    workingDir = src
    environment("ANDROID_NDK_HOME", android.ndkDirectory.path)
    commandLine(
        "cargo", "ndk", "-t", "arm64-v8a", "-t", "x86_64", "--platform", "28",
        "-o", jni.get().asFile.path, "build", "-p", "mobile", "--release",
    )
    onlyIf { !skipNode }
}

tasks.named("preBuild") { dependsOn(node) }

dependencies {
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
}
