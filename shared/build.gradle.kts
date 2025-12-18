import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.1.0")

            compileOnly("androidx.media3:media3-exoplayer:1.8.0")
            compileOnly("androidx.media3:media3-exoplayer-hls:1.8.0")
            implementation("org.slf4j:slf4j-simple:2.0.9")
        }

        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.1.0") }

        commonMain.dependencies {
            implementation(kotlin("stdlib"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
            implementation("io.ktor:ktor-utils:3.1.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            implementation("io.ktor:ktor-server-core:3.1.0")
            implementation("io.ktor:ktor-server-cio:3.1.0")
            implementation("io.ktor:ktor-client-core:3.1.0")
            implementation(compose.components.resources)
            implementation(compose.runtime)
        }
    }
}

android {
    namespace = "com.novage.p2pml.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}
