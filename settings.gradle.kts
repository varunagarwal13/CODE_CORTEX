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
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "vocis"
include(":app")

gradle.beforeProject {
    if (projectDir.absolutePath.contains("OneDrive", ignoreCase = true)) {
        val userHome = System.getProperty("user.home").replace("\\", "/")
        layout.buildDirectory.set(file("$userHome/.gradle_builds/${rootProject.name}/${project.name}"))
    }
}
