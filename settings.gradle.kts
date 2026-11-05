pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Only the Pleos SDKs come from this repository.
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content { includeGroup("ai.pleos.playground") }
        }
    }
}
rootProject.name = "MotionCare"
include(":app", ":core:model", ":core:ui", ":data:profile", ":data:trip", ":feature:driving", ":feature:history")
