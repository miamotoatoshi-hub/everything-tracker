import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/** Reads a value from gradle.properties (project or ~/.gradle), falling back to [default]. */
fun projectProp(name: String, default: String): String =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() } ?: default

/** Reads a secret from Gradle properties or from the environment (never commit these). */
fun secret(name: String): String? =
    ((findProperty(name) as String?) ?: System.getenv(name))?.takeIf { it.isNotBlank() }

android {
    // `namespace` is the Kotlin package / R class. It does NOT have to match the
    // applicationId, so changing TRACKLY_APPLICATION_ID never requires a refactor.
    namespace = "com.example.trackly"
    compileSdk = 36

    defaultConfig {
        applicationId = projectProp("TRACKLY_APPLICATION_ID", "com.example.trackly")
        minSdk = 26
        targetSdk = 36
        versionCode = projectProp("TRACKLY_VERSION_CODE", "1").toInt()
        versionName = projectProp("TRACKLY_VERSION_NAME", "1.0.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "BILLING_PREMIUM_PRODUCT_ID",
            "\"${projectProp("BILLING_PREMIUM_PRODUCT_ID", "trackly_premium_monthly")}\"",
        )
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${projectProp("PRIVACY_POLICY_URL", "")}\"")
        buildConfigField("String", "TERMS_OF_SERVICE_URL", "\"${projectProp("TERMS_OF_SERVICE_URL", "")}\"")
    }

    val keystorePath = secret("KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = rootProject.file(keystorePath)
                storePassword = secret("KEYSTORE_PASSWORD")
                keyAlias = secret("KEY_ALIAS")
                keyPassword = secret("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Without KEYSTORE_PATH the release build is produced unsigned; sign it
            // with your upload key before uploading to Google Play (see README).
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Keep every language in the base APK so in-app language switching works with AAB.
    bundle {
        language {
            enableSplit = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.billing.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
