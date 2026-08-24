import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
    namespace = "com.joeabouserhal.financetracker"
    compileSdk = 36

    // Local secrets/build props (gitignored). Gradle project properties
    // (-P flags / gradle.properties) win over local.properties.
    val localProps = Properties().apply {
      val f = rootProject.file("local.properties")
      if (f.exists()) f.inputStream().use { load(it) }
    }
    fun prop(name: String): String =
      (project.findProperty(name) as String?) ?: localProps.getProperty(name) ?: ""

    defaultConfig {
        applicationId = "com.joeabouserhal.financetracker"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Supabase credentials. Empty means "not configured" — the
        // app runs in guest mode only.
        buildConfigField("String", "SUPABASE_URL", "\"${prop("supabaseUrl")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${prop("supabaseAnonKey")}\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${prop("googleServerClientId")}\"")
    }

    signingConfigs {
        // Release keystore lives in keystore/ (gitignored); credentials in
        // local.properties. Missing config → release builds stay unsigned.
        create("release") {
            val keystorePath = prop("releaseKeystorePath")
            if (keystorePath.isNotBlank()) {
                storeFile = rootProject.file(keystorePath)
                storePassword = prop("releaseKeystorePassword")
                keyAlias = prop("releaseKeyAlias")
                keyPassword = prop("releaseKeyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (prop("releaseKeystorePath").isNotBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Offline-first local store
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.work.runtime.ktx)

  coreLibraryDesugaring(libs.desugar.jdk.libs)

  // Supabase + Ktor (backend phase)
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.auth.kt)
  implementation(libs.supabase.postgrest.kt)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.kotlinx.serialization.json)

  // Google sign-in (Credential Manager)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)
  implementation(libs.googleid)
}
