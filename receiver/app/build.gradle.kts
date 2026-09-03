plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ygsync.receiver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ygsync.receiver"
        minSdk = 26
        targetSdk = 35

        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        create("ygsync") {
            val keystorePath =
                project.findProperty("ygsyncKeystore") as String?

            val storePassword =
                project.findProperty("ygsyncStorePassword") as String?

            val keyAlias =
                project.findProperty("ygsyncKeyAlias") as String?

            val keyPassword =
                project.findProperty("ygsyncKeyPassword") as String?

            if (
                keystorePath != null &&
                storePassword != null &&
                keyAlias != null &&
                keyPassword != null
            ) {
                storeFile = file(keystorePath)
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("ygsync")
        }

        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ygsync")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(
        platform(
            "androidx.compose:compose-bom:2024.12.01"
        )
    )

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0"
    )

    debugImplementation(
        "androidx.compose.ui:ui-tooling"
    )
}
