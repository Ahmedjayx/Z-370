plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "works.axiora.z370"
    compileSdk = 35

    defaultConfig {
        applicationId = "works.axiora.z370launcher"
        minSdk = 30
        targetSdk = 34
        versionCode = runNumber
        versionName = "0.1.$runNumber"
        // The head unit is a 64-bit ARM Snapdragon: ship only that, not x86/32-bit map engines.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("sideload") {
            storeFile = file("z370.jks")
            storePassword = "z370launcher"
            keyAlias = "z370"
            keyPassword = "z370launcher"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
        }
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Compress the native map engine inside the APK: smaller download, unpacked once at install.
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Screenshot tests: render each page on the JVM (Robolectric) so the layout can be checked in CI.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.26.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.26.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
