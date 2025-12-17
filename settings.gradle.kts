pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://mvn.players.castlabs.com/")
            content {
                includeGroupAndSubgroups("com.castlabs")
                includeGroup("com.google.android.exoplayer")
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/FastPix/android-data-exo-player-sdk")
            credentials {
                val props = java.util.Properties()
                val localPropsFile = file("${rootDir}/local.properties")

                if (localPropsFile.exists()) {
                    props.load(localPropsFile.inputStream())
                }
                username = props.getProperty("gpr.user")
                password = props.getProperty("gpr.key")
            }
        }
    }
}

rootProject.name = "AgnoPlayerDataSDK"
include(":app")
include(":castLabs-player-data")
include(":android-data-core")
project(":android-data-core").projectDir = file("android-data-core/android-data-core-sdk")
