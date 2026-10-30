plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val alphaVersionCode = providers.gradleProperty("HALIMEO_VERSION_CODE")
    .orElse("1")
    .get()
    .toInt()
val alphaVersionName = providers.gradleProperty("HALIMEO_VERSION_NAME")
    .orElse("1.0.$alphaVersionCode")
    .get()

android {
    namespace = "it.halimeo.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "it.halimeo.app"
        minSdk = 26
        targetSdk = 37
        versionCode = alphaVersionCode
        versionName = alphaVersionName
        val apiBaseUrl = (project.findProperty("HALIMEO_API_BASE_URL") as String?) ?: "https://halimeo.example/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("int", "APP_VERSION_CODE", alphaVersionCode.toString())
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Haze: blur of the content behind floating glass surfaces, following Morphe’s pattern.
    implementation("dev.chrisbanes.haze:haze:2.0.1")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Material 3 Expressive APIs are available in the 1.5 beta line.
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.metrics:metrics-performance:1.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
