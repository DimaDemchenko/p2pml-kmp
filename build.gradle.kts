plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false

    // `apply false` loads these once in the root scope so both modules share one
    // copy of their build-service classes. Without it: "Cannot set the value of task
    // ':p2pml:spotlessKotlin' property 'taskService'".
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply false
}
