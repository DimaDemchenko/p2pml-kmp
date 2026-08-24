import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.diffplug.spotless")
    id("io.gitlab.arturbosch.detekt")
}

val ktlintVersion =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findVersion("ktlint")
        .get()
        .requiredVersion

// isolated.rootProject is the project-isolation-safe way to reach the root directory;
// touching rootProject directly is what a cross-project configuration block does wrong.
val detektConfig = isolated.rootProject.projectDirectory.file("detekt.yml")

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/generated/**/*.kt")

        ktlint(ktlintVersion)
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
    }
}

detekt {
    config.setFrom(detektConfig)
    buildUponDefaultConfig = true

    parallel = true

    source.setFrom("src")
}

tasks.withType<Detekt>().configureEach {
    exclude("**/generated/**")
}
