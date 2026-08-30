import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

/**
 * Opt-in walk-up GPS simulator (APS approach).
 * Enable with `-PincludeSimulator=true` or env `INCLUDE_SIMULATOR=1`.
 * When off, [com.ukrailtracker.app.debug.DebugWalkUp] is a no-op and simulator classes are omitted.
 */
val includeSimulator: Boolean =
    (findProperty("includeSimulator") as String?)?.equals("true", ignoreCase = true) == true ||
        System.getenv("INCLUDE_SIMULATOR") == "1"

android {
    namespace = "com.ukrailtracker.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ukrailtracker.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Rail Data Marketplace consumer keys (x-apikey). Board key falls back to legacy DARWIN_LDB_TOKEN.
        val ldbApiKey = localProperties.getProperty("DARWIN_LDB_API_KEY")
            ?: localProperties.getProperty("DARWIN_LDB_TOKEN")
            ?: ""
        val serviceDetailsApiKey =
            localProperties.getProperty("DARWIN_LDB_SERVICE_DETAILS_API_KEY") ?: ""
        buildConfigField("String", "DARWIN_LDB_API_KEY", "\"$ldbApiKey\"")
        buildConfigField("String", "DARWIN_LDB_SERVICE_DETAILS_API_KEY", "\"$serviceDetailsApiKey\"")
        buildConfigField("boolean", "INCLUDE_SIMULATOR", includeSimulator.toString())
    }

    buildTypes {
        release {
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        fun applySimulatorSources(name: String) {
            getByName(name) {
                if (includeSimulator) {
                    java.srcDir("src/simulator/java")
                    res.srcDir("src/simulator/res")
                } else {
                    java.srcDir("src/noSimulator/java")
                }
            }
        }
        applySimulatorSources("debug")
        applySimulatorSources("release")
        if (includeSimulator) {
            getByName("testDebug") {
                java.srcDir("src/testSimulator/java")
            }
        }
    }
}

// Cursor / IntelliJ Gradle import calls .get() on androidTest BuildConfig output dirs
// (generate*AndroidTestBuildConfig) during configuration, which Gradle 8.9 rejects.
// This project has no instrumented tests, so disable the variant.
androidComponents {
    beforeVariants { builder ->
        builder.enableAndroidTest = false
    }
}

if (includeSimulator) {
    logger.lifecycle("includeSimulator=true — packaging walk-up GPS simulator")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    // Real org.json for JVM unit tests (android.jar stubs throw by default)
    testImplementation("org.json:json:20240303")
}

// Copy repo-root stations.xml into assets as stations.json (do not commit the generated copy).
val copyStationsAsset by tasks.registering(Copy::class) {
    from(rootProject.file("../stations.xml"))
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "stations.json" }
}

tasks.named("preBuild").configure {
    dependsOn(copyStationsAsset)
}
