plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.vadik.raspisanie"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vadik.raspisanie"
        minSdk = 26
        targetSdk = 34
        // номер сборки GitHub -> новая версия ставится поверх старой
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "2.3." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Ключ подписи MyGub хранится только в секретах GitHub (MYGUB_KEYSTORE), в репозитории его нет.
    // Без ключа релиз не соберётся — так никто не выпустит «официальную» сборку от имени автора.
    val keystorePath = System.getenv("MYGUB_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("mygub") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MYGUB_KEYSTORE_PASSWORD")
                keyAlias = "mygub"
                keyPassword = System.getenv("MYGUB_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: сжатие и запутывание кода — разобрать и переделать APK заметно труднее
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("mygub")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // QR-код «Поделиться приложением»
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
