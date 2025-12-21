import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.novage.p2pml.shared"
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = libs.versions.android.min.sdk.get().toInt()

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }

        androidResources {
            enable = true
        }
    }

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.slf4j.simple)


            compileOnly(libs.androidx.media3.exoplayer)
            compileOnly(libs.androidx.media3.exoplayer.hls)
        }

        iosMain.dependencies { implementation(libs.ktor.client.darwin) }

        commonMain.dependencies {
            implementation(kotlin("stdlib"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.utils)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.server.cors)
            implementation(compose.components.resources)
            implementation(compose.runtime)
        }
    }
}
