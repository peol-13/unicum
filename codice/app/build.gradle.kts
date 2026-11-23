import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.ksp)
    kotlin("plugin.serialization") version "2.0.21"
}

// Valori personali di chi compila (data dell'HRV, database Notion, obiettivo): file fuori dal repository,
// vedi personal.properties.example. Senza file l'app calcola tutto da sola.
val personal = Properties().apply {
    val f = rootProject.file("personal.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun personalField(key: String): String = "\"" + (personal.getProperty(key) ?: "").trim().replace("\"", "") + "\""

// Firma della release: keystore.properties fuori dal repository (vedi keystore.properties.example).
// Senza file la release esce non firmata.
val keystore = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.peol13.unicum"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.peol13.unicum"
        minSdk = 26
        targetSdk = 37
        // versionCode: +1 a ogni APK pubblicato (Android installa sopra solo se è più alto)
        versionCode = 4
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PERSONAL_HRV_FROM", personalField("hrv_from"))
        buildConfigField("String", "PERSONAL_NOTION_PLAN", personalField("notion_plan_database"))
        buildConfigField("String", "PERSONAL_NOTION_LOG", personalField("notion_log_database"))
        buildConfigField("String", "PERSONAL_RUN_GOAL_SHORT", personalField("run_goal_short"))
    }

    signingConfigs {
        if (keystore.getProperty("storeFile") != null) create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // Ottimizzazione su tutta l'app: limitarla ad alcuni pacchetti (packageScope) faceva andare in crash
            // la release all'avvio (IllegalAccessError tra classi ottimizzate e non).
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Gli schemi esportati da Room (app/schemas) servono ai test strumentati delle migrazioni
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Serializzazione JSON (usata dal repository attuale)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Health Connect: dati di Samsung Health, Ultrahuman e altre app
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Scansione codici a barre: CameraX + ML Kit (modello incluso nell'app, funziona offline)
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Lettura del testo dei programmi di allenamento in PDF (Apache 2.0)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Room: database locale
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    // org.json di Android è solo uno stub nei test sulla JVM
    testImplementation("org.json:json:20231013")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("androidx.compose.material:material-icons-extended")
}