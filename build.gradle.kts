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
    ":app" to setOf(":core:model", ":core:ui", ":data:trip", ":feature:driving", ":feature:history"),
    ":core:model" to emptySet(),
    ":core:ui" to emptySet(),
    ":data:trip" to setOf(":core:model"),
    ":feature:driving" to setOf(":core:model", ":core:ui", ":data:trip"),
    ":feature:history" to setOf(":core:model", ":core:ui", ":data:trip"),
)
val verifyArchitecture by tasks.registering {
    group = "verification"
    doLast {
        subprojects.forEach { module ->
            val allowed = allowedEdges[module.path].orEmpty()
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
subprojects {
    dependencyLocking { lockAllConfigurations() }
    tasks.matching { it.name == "preBuild" || it.name == "compileKotlin" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyArchitecture"))
    }
}
tasks.register("verifyAll") {
    group = "verification"
    description = "Build the app and run all host tests and Android lint checks."
    dependsOn(verifyArchitecture, ":app:assembleDebug", ":core:model:test")
    listOf(":app", ":core:ui", ":data:trip", ":feature:driving", ":feature:history").forEach {
        dependsOn("$it:testDebugUnitTest", "$it:lintDebug")
    }
}
