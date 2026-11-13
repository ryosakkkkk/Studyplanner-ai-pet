import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "pe.studyplanner.aipet"
    compileSdk = 36
    defaultConfig {
        applicationId = "pe.studyplanner.aipet"
        minSdk = 26
        targetSdk = 36
        versionCode = 41
        versionName = "0.24.2"
        // Conexión opcional al servidor de IA (GPT-5 mini o IA gratuita): ACTIVAR_IA.bat crea ia_local.properties (no se sube a git).
        val ia = Properties().apply {
            val file = rootProject.file("ia_local.properties")
            if (file.exists()) file.inputStream().use { load(it) }
        }
        fun quoted(value: String) = "\"" + value.replace("\\", "").replace("\"", "") + "\""
        buildConfigField("String", "AI_ENDPOINT", quoted(ia.getProperty("endpoint", "")))
        buildConfigField("String", "AI_TOKEN", quoted(ia.getProperty("token", "")))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true }
}

dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.android)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
