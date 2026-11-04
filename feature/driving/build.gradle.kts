import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
val androidConfig = Properties().apply {
    rootProject.file("config/android-test.properties").inputStream().use { load(it) }
}
android {
    namespace = "dev.motioncare.feature.driving"
    compileSdk = androidConfig.getProperty("compileSdk").toInt()
    buildToolsVersion = androidConfig.getProperty("buildTools")
    defaultConfig { minSdk = androidConfig.getProperty("minSdk").toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += setOf("OldTargetApi", "AndroidGradlePluginVersion", "GradleDependency")
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":data:trip"))
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.coroutines.android)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
}
