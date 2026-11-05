// Root build file. Plugin versions are pinned here once; modules only apply them.
// Kotlin must match the version used by ../core (2.1.20). Two different Kotlin Gradle
// plugin versions in one build fail to configure.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}
