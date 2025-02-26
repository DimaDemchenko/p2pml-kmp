import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
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
        androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.1.0") }

        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.1.0") }

        commonMain.dependencies {
            implementation(kotlin("stdlib"))
            implementation("io.ktor:ktor-utils:3.1.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            implementation("io.ktor:ktor-server-core:3.1.0")
            implementation("io.ktor:ktor-server-cio:3.1.0")
            implementation("io.ktor:ktor-client-core:3.1.0")
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

val generatedFile =
    file("src/commonMain/kotlin/com/novage/p2pml/resources/GeneratedStaticResources.kt")
val backupFile = file("build/backup/GeneratedStaticResources.kt")

val backupStaticResources by
    tasks.registering {
        doLast {
            if (generatedFile.exists()) {
                backupFile.parentFile.mkdirs()
                generatedFile.copyTo(backupFile, overwrite = true)

                println("Backed up ${generatedFile.absolutePath} to ${backupFile.absolutePath}")
            }
        }
    }

val updateSourceStaticResources by
    tasks.registering {
        dependsOn(backupStaticResources)
        // Input: the HTML file you want to embed.
        val indexHtmlFile = file("src/commonMain/resources/static/index.html")
        val jsFile = file("src/commonMain/resources/static/js/p2p-media-loader-core.es.min.js")

        doLast {
            val indexHtmlContent =
                indexHtmlFile.readText().replace("\"", "\\\"").replace("\n", "\\n").replace(
                    Regex("""\$""")
                ) {
                    "\\$"
                }

            val jsContent =
                jsFile
                    .readText()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace(Regex("""\$""")) { "\\$" }

            val kotlinCode =
                """
            |package com.novage.p2pml.resources
            |
            |const val INDEX_HTML: String = "$indexHtmlContent"
            |const val P2PML_CORE_JS: String = "$jsContent"
            |
        """
                    .trimMargin()
            // Ensure the folder exists and write the generated code.
            generatedFile.parentFile.mkdirs()
            generatedFile.writeText(kotlinCode)
            println("Updated ${generatedFile.absolutePath} with generated content.")
        }
    }

val restoreSourceStaticResources by
    tasks.registering {
        doLast {
            if (backupFile.exists()) {
                backupFile.copyTo(generatedFile, overwrite = true)
                println("Restored backup of ${generatedFile.absolutePath}")
            }
        }
    }

tasks
    .matching { it.name.startsWith("compileKotlin") }
    .configureEach { dependsOn(updateSourceStaticResources) }

tasks.named("build") { finalizedBy(restoreSourceStaticResources) }
