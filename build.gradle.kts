plugins {
    id("com.android.application") version "9.1.1" apply false
    // AGP 9.1.1 provides built-in Kotlin support; the Compose compiler plugin remains explicit.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
