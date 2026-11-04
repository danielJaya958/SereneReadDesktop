import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.sereneread.project.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SereneRead"
            // Release builds pass -PappVersion from the Git tag; local builds use the fallback.
            packageVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")
            description = "Ebook reader for EPUB, PDF, and TXT"
            // From suggestRuntimeModules. Without java.sql the packaged app cannot open its SQLite database.
            modules("java.instrument", "java.management", "java.net.http", "java.sql", "jdk.security.auth", "jdk.unsupported")

            windows {
                menu = true
                shortcut = true
                // Must stay the same across versions, so a new MSI replaces the old install.
                upgradeUuid = "1e2a2bce-0bf2-42e6-9838-ae58dcf28002"
            }
        }
    }
}