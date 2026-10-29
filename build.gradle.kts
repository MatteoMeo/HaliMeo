plugins {
    id("com.android.application") version "9.5.0" apply false
    // Keep the Compose compiler aligned with the current Kotlin compiler.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.10" apply false
}
