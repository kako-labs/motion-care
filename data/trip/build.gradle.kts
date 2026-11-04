import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
val androidConfig = Properties().apply {
    rootProject.file("config/android-test.properties").inputStream().use { load(it) }
}
android {
    namespace = "dev.motioncare.data.trip"
    compileSdk = androidConfig.getProperty("compileSdk").toInt()
    useLibrary("android.car")
    buildToolsVersion = androidConfig.getProperty("buildTools")
    defaultConfig { minSdk = androidConfig.getProperty("minSdk").toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += setOf("OldTargetApi", "AndroidGradlePluginVersion", "GradleDependency")
    }
}
dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.android)
    testImplementation(libs.coroutines.test)
    implementation(libs.core.ktx)
    implementation(libs.pleos.vehicle)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
}
