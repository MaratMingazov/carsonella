rootProject.name = "carsonella"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")
include(":server")
include(":shared")


/**
 * # только компиляция main-кода (быстро)
 *     ./gradlew :composeApp:compileKotlinJvm
 *
 * # компиляция + тесты shared
 *         ./gradlew :composeApp:compileKotlinJvm :shared:jvmTest
 *
 * # запустить само десктоп-приложение
 *     ./gradlew :composeApp:run
 *         ./gradlew :composeApp:compileKotlinJvm
 *
 * # компиляция + тесты shared
 *         ./gradlew :composeApp:compileKotlinJvm :shared:jvmTest
 *
 * # запустить само десктоп-приложение
 *     ./gradlew :composeApp:run
 *
 *         Если хочется прогнать «всё как в проверке» одной строкой:
 *
 * ./gradlew :composeApp:compileKotlinJvm :shared:compileTestKotlinJvm :shared:jvmTest
 */

