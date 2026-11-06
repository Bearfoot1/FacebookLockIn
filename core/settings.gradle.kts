pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // The version lives here, not in build.gradle.kts. When core is included in the
    // android build, the Kotlin plugin is already on the classpath from there, and a
    // second version declaration fails to configure.
    plugins {
        kotlin("jvm") version "2.1.20"
    }
}

rootProject.name = "lockin-core"
