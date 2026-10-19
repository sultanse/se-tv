plugins {
    id("com.android.application")
}

android {
    namespace = "com.se.simpletv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.se.simpletv"
        minSdk = 23
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The .kt files live directly in src/main, next to the manifest, instead of src/main/kotlin.
    sourceSets["main"].kotlin.srcDir("src/main")
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
}
