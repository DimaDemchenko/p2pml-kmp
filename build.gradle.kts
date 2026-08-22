plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false

    // Loaded here, applied by the p2pml.quality convention plugin. Spotless and detekt use
    // shared build services; without a single root-level classloader scope each module gets
    // its own copy of the service type and task wiring fails.
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
}
