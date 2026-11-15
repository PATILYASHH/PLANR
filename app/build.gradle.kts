plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

// Release CI passes -PplanrVersionName=1.2.3 -PplanrVersionCode=10203 from the git tag.
val planrVersionName = (findProperty("planrVersionName") as String?) ?: "0.6.0"
val planrVersionCode = (findProperty("planrVersionCode") as String?)?.toInt() ?: 600

// Release signing comes from environment variables (GitHub secrets). See
// docs/SIGNING.md and scripts/create-release-key.sh.
val releaseKeystore = System.getenv("PLANR_KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.exosynk.planr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.exosynk.planr"
        minSdk = 30
        targetSdk = 36
        versionCode = planrVersionCode
        versionName = planrVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("PLANR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PLANR_KEY_ALIAS")
                keyPassword = System.getenv("PLANR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Signed only with the ExoSynk Labs release key. Without it the release APK is
            // left unsigned (F-Droid signs its own builds); it never falls back to the
            // debug key, whose signature differs on every machine and CI run.
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests; use the real one there.
    testImplementation(libs.org.json)
    "ksp"(libs.androidx.room.compiler)
}
