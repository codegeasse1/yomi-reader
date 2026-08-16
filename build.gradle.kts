buildscript {
    dependencies {
        classpath(libs.android.shortcut.gradle)
    }
}

plugins {
    alias(androidx.plugins.application) apply false
    alias(androidx.plugins.library) apply false
    alias(androidx.plugins.test) apply false
    alias(androidx.plugins.kmp.library) apply false
    alias(kotlinx.plugins.compose.compiler) apply false
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.aboutLibrariesAndroid) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
    // Required for Spotless 8.x multi-project shared task service (diffplug/spotless#2877).
    alias(libs.plugins.spotless) apply false
}

// Dynamically loaded extension APKs are compiled against the kotlinx.coroutines classes that
// are bundled inside the app APK, so those classes are part of the app's public ABI. If any
// module or transitive dependency resolves a different coroutines version, extensions break at
// runtime with linkage errors such as
// `NoSuchMethodError: kotlinx.coroutines.BuildersKt.runBlockingK$default`.
// Pin the version declared in gradle/kotlinx.versions.toml for every project and configuration,
// so the bundled ABI cannot drift through dependency resolution.
val coroutinesVersion = kotlinx.coroutines.bom.get().version
    ?: error("kotlinx-coroutines-bom must declare an explicit version")

allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines")) {
                useVersion(coroutinesVersion)
                because("Extensions link against the coroutines ABI bundled in the app APK")
            }
        }
    }
}

val buildLogic = gradle.includedBuild("build-logic")

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
    dependsOn(buildLogic.task(":clean"))
}
