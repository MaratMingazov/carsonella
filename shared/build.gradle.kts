import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

// Android — по флагу, как в composeApp: без него AGP не применяется вовсе. Пока флага нет, применённый
// AGP только печатал бы на каждую сборку свои deprecation'ы (multi-string notation в lint-gradle/aapt2).
val withAndroid: Boolean = (findProperty("withAndroid") as? String)?.toBoolean() ?: false

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary) apply false
}

if (withAndroid) {
    apply(plugin = libs.plugins.androidLibrary.get().pluginId)
}

kotlin {
    if (withAndroid) {
        androidTarget {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }

    iosArm64() // iosX64 нет — см. комментарий в composeApp/build.gradle.kts
    iosSimulatorArm64()

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    // Serve sources to debug inside browser
                    static(rootDirPath)
                    static(projectDirPath)
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

if (withAndroid) {
    extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
        namespace = "maratmingazovr.ai.carsonella.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        defaultConfig {
            minSdk = libs.versions.android.minSdk.get().toInt()
        }
    }
}
