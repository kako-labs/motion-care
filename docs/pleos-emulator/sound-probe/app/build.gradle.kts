plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.soundprobe"
    compileSdk = 35
    defaultConfig {
        applicationId = (findProperty("probeId") as String?) ?: "dev.soundprobe"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    if (hasProperty("noNet")) sourceSets["main"].manifest.srcFile("src/main/AndroidManifest-nonet.xml")
}

dependencies {
    implementation("ai.pleos.playground:TextToSpeech:3.3.1") { if (hasProperty("noUa")) exclude(group = "ai.pleos.playground.security") }
}
