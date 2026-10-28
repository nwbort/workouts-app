plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// CI stamps every build with its run number, so each APK installs over the
// previous one as an update. Local builds stay at 1.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.nwbort.workouts"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nwbort.workouts"
        // Built for a current Pixel: Android 16 and up, targeting Android 17.
        minSdk = 36
        targetSdk = 37
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        // A checked-in, stable sideload key: every build (local or CI) is
        // signed the same way, so a new APK installs over the old one instead
        // of failing with "App not installed". Not a Play Store upload key.
        create("sideload") {
            storeFile = file("workouts.keystore")
            storePassword = "workouts123"
            keyAlias = "workouts"
            keyPassword = "workouts123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric needs the merged resources and assets (the plan data).
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's Android 16 sandbox reaches into JDK internals.
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // Glance pulls in WorkManager 2.7 and Room 2.2, whose keep rules predate R8
    // full mode: the shrunk APK crashed at launch unable to construct
    // WorkManager's database. Current versions ship rules that work.
    implementation(libs.androidx.work.runtime)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Newer than the Compose test library pulls in: 3.5 can't idle on Android 17.
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.glance.appwidget.testing)
}
