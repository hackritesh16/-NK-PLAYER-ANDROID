import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nkplayer.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nkplayer.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0-glass"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Brand fonts (Unbounded for headings, Manrope for text; both SIL OFL) are downloaded once
// into res/font before every build, so no binary font files need to live in git.
val fontDir = file("src/main/res/font")
val fontFiles = mapOf(
    "unbounded.ttf" to "https://raw.githubusercontent.com/google/fonts/main/ofl/unbounded/Unbounded%5Bwght%5D.ttf",
    "manrope.ttf" to "https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf"
)
val fetchFonts = tasks.register("fetchFonts") {
    outputs.dir(fontDir)
    doLast {
        fontDir.mkdirs()
        fontFiles.forEach { (name, url) ->
            val target = File(fontDir, name)
            if (!target.exists() || target.length() < 50_000L) {
                var attempt = 0
                while (true) {
                    try {
                        URL(url).openStream().use { input ->
                            target.outputStream().use { out -> input.copyTo(out) }
                        }
                        break
                    } catch (e: Exception) {
                        attempt++
                        if (attempt >= 3) throw GradleException("Could not download font ${name}: ${e.message}")
                        Thread.sleep(1500)
                    }
                }
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchFonts) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("dev.chrisbanes.haze:haze:1.3.0")
    implementation("dev.chrisbanes.haze:haze-materials:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
