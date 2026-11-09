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
        val apiBaseUrl = (project.findProperty("HALIMEO_API_BASE_URL") as String?) ?: "https://api.halimeo.bid/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"609968712989-909cckera569ft2g5ivrp79obp7ij4hn.apps.googleusercontent.com\"")
        buildConfigField("String", "FACEBOOK_APP_ID", "\"2135436027084791\"")
        manifestPlaceholders["facebookAppId"] = "fb2135436027084791"
        manifestPlaceholders["facebookClientToken"] = "075f78dc18a614e2fa460b99e8796151"
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
    implementation("dev.chrisbanes.haze:haze:2.1.0")
    implementation("dev.chrisbanes.haze:haze-blur:2.1.0")
    implementation("dev.chrisbanes.haze:haze-glass:2.1.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Material 3 Expressive APIs are available in the 1.5 beta line.
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-rc01")
    // Adaptive layouts for phones, tablets, foldables and resizable windows.
    implementation("androidx.compose.material3.adaptive:adaptive:1.4.0-alpha03")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.4.0-alpha03")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.4.0-alpha03")
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    implementation("com.facebook.android:facebook-login:19.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("androidx.webkit:webkit:1.18.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.metrics:metrics-performance:1.0.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
