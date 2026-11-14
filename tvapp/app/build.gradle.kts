import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Chave de assinatura fica fora do repositorio (o repo e publico). Sem o arquivo, o release sai sem assinatura.
val chave = file("C:/dev/chaves/tvfacil.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    signingConfigs {
        if (chave != null) {
            create("release") {
                storeFile = file(chave.getProperty("storeFile"))
                storePassword = chave.getProperty("storePassword")
                keyAlias = chave.getProperty("keyAlias")
                keyPassword = chave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (chave != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    namespace = "br.tvfacil"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.tvfacil"
        minSdk = 29
        targetSdk = 36
        versionCode = 22
        versionName = "0.22"
        // O stick e 32 bits: so essa arquitetura deixa o APK bem menor.
        ndk { abiFilters += "armeabi-v7a" }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.videolan.android:libvlc-all:3.7.7")}
