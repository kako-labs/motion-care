import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// One explicit graph. Gradle supplies the actual dependencies; no source regex parsing.
val allowedEdges = mapOf(
    ":app" to setOf(":core:model", ":core:ui", ":data:profile", ":data:trip", ":feature:driving", ":feature:history"),
    ":core:model" to emptySet(),
    ":core:ui" to emptySet(),
    ":data:profile" to setOf(":core:model"),
    ":data:trip" to setOf(":core:model"),
    ":feature:driving" to setOf(":core:model", ":core:ui", ":data:trip"),
    ":feature:history" to setOf(":core:model", ":core:ui", ":data:profile", ":data:trip"),
)
val verifyArchitecture by tasks.registering {
    group = "verification"
    doLast {
        // Folders such as :core have no build file and are not modules.
        subprojects.filter { it.buildFile.exists() }.forEach { module ->
            val allowed = checkNotNull(allowedEdges[module.path]) {
                "Declare ${module.path} in allowedEdges before adding the module"
            }
            module.configurations.forEach { config ->
                config.dependencies.withType<ProjectDependency>().forEach { dependency ->
                    check(dependency.path == module.path || dependency.path in allowed) {
                        "Forbidden module dependency: ${module.path} -> ${dependency.path}"
                    }
                }
            }
        }
    }
}
val verifyAll = tasks.register("verifyAll") {
    group = "verification"
    description = "Build the app and run all host tests and Android lint checks."
    dependsOn(verifyArchitecture, ":app:assembleDebug")
}
subprojects {
    dependencyLocking { lockAllConfigurations() }
    tasks.matching { it.name == "preBuild" || it.name == "compileKotlin" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyArchitecture"))
    }
    // New modules join verification from their plugin; nothing to remember per module.
    val module = path
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        verifyAll.configure { dependsOn("$module:test") }
    }
    listOf("com.android.application", "com.android.library").forEach { plugin ->
        pluginManager.withPlugin(plugin) {
            verifyAll.configure { dependsOn("$module:testDebugUnitTest", "$module:lintDebug") }
        }
    }
}
