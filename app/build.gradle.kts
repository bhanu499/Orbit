import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProp(key: String): String =
    localProps.getProperty(key, "").replace("\"", "\\\"")

android {
    namespace = "com.orbit.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.orbit.assistant"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-multi-llm"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Production builds use backend proxy; dev keys are never embedded in release APKs.
        buildConfigField("String", "ORBIT_PROXY_BASE_URL", "\"${localProp("ORBIT_PROXY_BASE_URL")}\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEV_GEMINI_API_KEY", "\"${localProp("ORBIT_DEV_GEMINI_KEY")}\"")
            buildConfigField("String", "DEV_OPENAI_API_KEY", "\"${localProp("ORBIT_DEV_OPENAI_KEY")}\"")
            buildConfigField("String", "DEV_GROQ_API_KEY", "\"${localProp("ORBIT_DEV_GROQ_KEY")}\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "DEV_GEMINI_API_KEY", "\"\"")
            buildConfigField("String", "DEV_OPENAI_API_KEY", "\"\"")
            buildConfigField("String", "DEV_GROQ_API_KEY", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
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
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation-compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
