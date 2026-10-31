plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The collector endpoint is injected at build time so debug builds can talk to
// the local renderer while release builds point at the CI-published HTTPS JSON.
// Debug defaults to localhost; release has no default and must be configured.
val glmCollectorUrl: String = providers.gradleProperty("glmCollectorUrl")
    .orNull
    ?.trim()
    .orEmpty()

val releaseVersionCode: Int = providers.gradleProperty("releaseVersionCode")
    .orNull
    ?.let { value -> requireNotNull(value.toIntOrNull()) { "releaseVersionCode must be an integer" } }
    ?: 1
val releaseVersionName: String = providers.gradleProperty("releaseVersionName")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "1.0"

// The release key is supplied by the repository owner through CI secrets. It is
// never committed. When any value is absent the release build stays unsigned so
// local and PR builds keep working.
val releaseKeystorePath: String = System.getenv("RELEASE_KEYSTORE_PATH").orEmpty()
val releaseKeystorePassword: String = System.getenv("RELEASE_KEYSTORE_PASSWORD").orEmpty()
val releaseKeyAlias: String = System.getenv("RELEASE_KEY_ALIAS").orEmpty()
val releaseKeyPassword: String = System.getenv("RELEASE_KEY_PASSWORD").orEmpty()
val hasReleaseSigning: Boolean = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { it.isNotBlank() }

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.aioffer.radar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aioffer.radar"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "GLM_COLLECTOR_URL",
                glmCollectorUrl.ifBlank { "http://127.0.0.1:8787/v1/glm-coding-plan" }
                    .asBuildConfigString()
            )
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            buildConfigField(
                "String",
                "GLM_COLLECTOR_URL",
                glmCollectorUrl.asBuildConfigString()
            )
            isMinifyEnabled = false
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

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
