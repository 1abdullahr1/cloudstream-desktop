import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm {}
    sourceSets {
        jvmMain.dependencies {
            implementation(libs.bundles.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.java)
            implementation(libs.vlcj)
            implementation(libs.dex.translator) {
                exclude(group = "com.android.tools", module = "r8")
            }
            implementation("com.android.tools:r8:8.3.37")
            implementation(compose.desktop.currentOs) {
                // compose.desktop.currentOs imports the wrong material 2, so we exclude it
                exclude(group = "org.jetbrains.compose.material", module = "material")
            }
            implementation(project(":shared"))
            implementation(project(":library"))
        }
    }
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "com.android.tools" && requested.name == "r8") {
                useVersion("8.3.37")
                because("com.android.tools:r8:8.3.0 is not available in Google Maven, whereas 8.3.37 is")
            }
        }
    }
}

// java.lang.System::load has been called by org.jetbrains.skiko.LibraryLoader in an unnamed module
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

compose.desktop {
    application {
        mainClass = "com.lagradost.cloudstream4.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "CloudStream"
            packageVersion = "1.0.0"
            description = "CloudStream for Windows"
            vendor = "CloudStream"

            val iconsRoot = project.file("src/desktop-icons")
            windows {
                iconFile.set(iconsRoot.resolve("icon-windows.ico"))
                menuGroup = "CloudStream"
                upgradeUuid = "e6c43491-b66e-4903-8898-d2182049e29a"
            }
            linux {
                iconFile.set(iconsRoot.resolve("icon-linux.png"))
            }
        }

        //buildTypes.release.proguard {
        //    configurationFiles.from(project.file("rules.pro"))
        //}
    }
}