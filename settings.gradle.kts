pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // مستودع Google مقصور على مجموعاته حتى لا تُطلب مكتبات Maven Central منه.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "masarif"

// وحدة core نقية (Kotlin/JVM) تُبنى وتُختبر بلا Android SDK.
include(":core")

// وحدة التطبيق تُضمَّن فقط عند توفر Android SDK، حتى يبقى بناء core ممكناً على أي جهاز.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.lifecycle("masarif: Android SDK غير موجود (ANDROID_HOME/local.properties)، وحدة :app لن تُضمَّن في هذا البناء.")
}
