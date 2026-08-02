# Android Native App — Implementation Spec

Concrete implementation guide for the Hello World milestone. Parent plan: [android-native-app-plan.md](./android-native-app-plan.md).

Work from the `android/` directory unless noted.

## Status

| Milestone | State |
|-----------|--------|
| Plan approved | Done |
| Toolchain install | Done (T1 + T2) |
| Project scaffold | Done (T3–T7) |
| Emulator (Pixel 8 Pro) verify | Done (install/launch; confirm neon UI visually) |
| Physical phone verify | Done (Pixel 8 Pro install/launch) |

---

## 0. Versions (pin these)

| Component | Version |
|-----------|---------|
| JDK | 17 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Gradle (wrapper) | 8.9 |
| Compose BOM | `2024.10.01` |
| `compileSdk` / `targetSdk` | 35 |
| `minSdk` | 26 |
| `applicationId` / `namespace` | `com.ukrailtracker.app` |
| AVD name | `ukrail_pixel8pro_api35` |
| AVD device | `pixel_8_pro` |
| System image | `system-images;android-35;google_apis;x86_64` |

If AGP/Kotlin require a different Compose compiler setup: use the Kotlin Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) with Kotlin 2.x (no separate `composeOptions.kotlinCompilerExtensionVersion`).

---

## 1. Toolchain

### 1.1 JDK

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
java -version   # 17.x
```

### 1.2 Android CLI (preferred)

Official Google package — installs the `android` CLI, which manages the SDK under `~/Android/Sdk` (see [Android CLI download](https://developer.android.com/tools/agents/android-cli/download)):

```bash
curl -fsSL https://dl.google.com/linux/linux_signing_key.pub | sudo tee /etc/apt/keyrings/google.asc >/dev/null
sudo sh -c 'echo "deb [arch=amd64 signed-by=/etc/apt/keyrings/google.asc] http://dl.google.com/android/cli/latest/debian/ stable main" > /etc/apt/sources.list.d/android-cli.list'
sudo apt-get update
sudo apt-get install android-cli
android --version
android info   # sdk: /home/<user>/Android/Sdk
```

### 1.3 Shell env (`~/.bash.d`)

In `~/.bash.d/env.sh`:

```bash
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

In `~/.bash.d/path.sh` (via `add_to_path`):

```bash
# platform-tools, emulator, and cmdline-tools/latest/bin if present
```

```bash
source ~/.bash.d/env.sh
source ~/.bash.d/path.sh
```

### 1.4 SDK packages

```bash
android sdk install \
  platform-tools \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  emulator \
  "system-images;android-35;google_apis;x86_64"

android sdk list
```

AVDs later via `android emulator create` (not classic `avdmanager`).

**Done when:** `adb version` and `emulator -version` succeed with `ANDROID_HOME` set.

---

## 2. File tree to create

```text
android/
  docs/
    android-native-app-plan.md
    android-native-app-impl.md          # this file
  .gitignore
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  local.properties                      # gitignored; sdk.dir=...
  gradlew
  gradlew.bat
  gradle/wrapper/gradle-wrapper.properties
  gradle/wrapper/gradle-wrapper.jar
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/ukrailtracker/app/MainActivity.kt
  app/src/main/java/com/ukrailtracker/app/ui/theme/Color.kt
  app/src/main/java/com/ukrailtracker/app/ui/theme/Theme.kt
  app/src/main/java/com/ukrailtracker/app/ui/theme/Type.kt
  app/src/main/res/values/strings.xml
  app/src/main/res/values/themes.xml
  app/src/main/res/xml/backup_rules.xml
  app/src/main/res/xml/data_extraction_rules.xml
  app/src/main/res/mipmap-*/…           # launcher icons (any simple placeholder)
```

---

## 3. Gradle files

### 3.1 `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "uk-rail-tracker"
include(":app")
```

### 3.2 Root `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

### 3.3 `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

### 3.4 `app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ukrailtracker.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ukrailtracker.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

Also add empty `app/proguard-rules.pro`.

### 3.5 Gradle wrapper

```bash
# One-time, if gradle is available globally:
gradle wrapper --gradle-version 8.9

# Or copy wrapper jar/scripts from a known-good AGP 8.7 project, with:
# gradle/wrapper/gradle-wrapper.properties → distributionUrl=...gradle-8.9-bin.zip
```

### 3.6 `local.properties` (do not commit)

```properties
sdk.dir=/home/<user>/Android/Sdk
```

### 3.7 `.gitignore`

```gitignore
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build/
/app/build/
/captures/
.externalNativeBuild/
.cxx/
*.apk
*.ap_
*.dex
```

---

## 4. App sources

### 4.1 Neon palette — `ui/theme/Color.kt`

| Token | Hex | Compose name |
|-------|-----|--------------|
| Background | `#0B0F1A` | `NeonBackground` |
| Surface | `#121826` | `NeonSurface` |
| Primary cyan | `#00F0FF` | `NeonCyan` |
| Secondary magenta | `#FF2BD6` | `NeonMagenta` |
| On-background | `#E8F7FF` | `NeonOnBackground` |
| Outline / grid | `#1E3A5F` | `NeonOutline` |
| Muted subtitle | `#8AA4C8` | `NeonMuted` |

### 4.2 `ui/theme/Theme.kt`

- Build `darkColorScheme` from the palette above.
- Expose `UkRailTrackerTheme { content }` that **always** applies the neon dark scheme (ignore system light mode).
- Set status bar / nav bar to match background via `enableEdgeToEdge()` + `SystemBarStyle` (or `WindowCompat`) in `MainActivity`.

### 4.3 `ui/theme/Type.kt`

- `Typography` with a large bold hero style (~36–40sp, `FontWeight.Bold`) and a smaller muted body (~14–16sp).
- System fonts OK for this milestone; swap to a distinctive face later if desired.

### 4.4 `MainActivity.kt` UI contract

Single composition, full-bleed neon canvas:

1. **Brand** — “UK Rail Tracker” in `NeonCyan` (hero-level, not a nav eyebrow).
2. **Headline** — “Hello World” with soft cyan text shadow / glow.
3. **Support** — one short muted line, e.g. “Live rail data, coming soon.”

Layout: centered `Column`, no cards, no pills, no stats. `Modifier.fillMaxSize()` + `NeonBackground`.

### 4.5 `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.UkRailTracker">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.UkRailTracker">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 4.6 Resources

**`res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">UK Rail Tracker</string>
    <string name="hello_world">Hello World</string>
    <string name="hello_subtitle">Live rail data, coming soon.</string>
</resources>
```

**`res/values/themes.xml`**

- `Theme.UkRailTracker` parented from `android:Theme.Material.NoActionBar` (or DayNight.NoActionBar).
- `android:statusBarColor` / `navigationBarColor` / `windowBackground` → `#0B0F1A`.

**Backup / data extraction XML:** minimal allow/deny stubs so manifest references resolve.

**Launcher icon:** generate a simple adaptive icon (solid dark + cyan mark) or use `mipmap` placeholders so the build links.

---

## 5. Build verification

```bash
cd android
./gradlew :app:assembleDebug
ls -la app/build/outputs/apk/debug/app-debug.apk
```

**Done when:** task `BUILD SUCCESSFUL` and APK exists.

---

## 6. Pixel 8 Pro emulator

### 6.1 Create AVD

```bash
avdmanager list device | grep -i pixel_8_pro

avdmanager create avd \
  --name ukrail_pixel8pro_api35 \
  --package "system-images;android-35;google_apis;x86_64" \
  --device "pixel_8_pro" \
  --force
```

### 6.2 Boot

```bash
emulator -avd ukrail_pixel8pro_api35 -netdelay none -netspeed full &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
```

### 6.3 Install + launch

```bash
./gradlew :app:installDebug
adb shell am start -n com.ukrailtracker.app/.MainActivity
```

### 6.4 Smoke checklist

- [ ] Pixel 8 Pro emulator running
- [ ] App opens without crash
- [ ] Dark `#0B0F1A` canvas
- [ ] Cyan brand + glowing “Hello World”
- [ ] Magenta available in theme (may be unused on this screen)
- [ ] No fatal logcat for `com.ukrailtracker.app`

Stop: `adb -s emulator-5554 emu kill`

---

## 7. Physical phone

1. Enable Developer options → USB debugging.
2. Connect USB; accept RSA prompt.
3. `adb devices` shows `device`.
4. `./gradlew :app:installDebug`
5. `adb shell am start -n com.ukrailtracker.app/.MainActivity`
6. Confirm neon Hello World on device.

Uninstall: `./gradlew :app:uninstallDebug`

---

## 8. Task checklist (execute in order)

- [x] **T1** Install JDK 17; set `JAVA_HOME`
- [x] **T2** Install android-cli + SDK packages; set `ANDROID_HOME` / `PATH`
- [x] **T3** Write root Gradle files + `.gitignore` + `local.properties`
- [x] **T4** Add Gradle wrapper (8.9)
- [x] **T5** Write `app/build.gradle.kts` + proguard stub
- [x] **T6** Implement `Color.kt` / `Type.kt` / `Theme.kt`
- [x] **T7** Implement `MainActivity` neon Hello World UI + strings/themes/manifest/icons
- [x] **T8** `./gradlew :app:assembleDebug` succeeds
- [x] **T9** Create/start Pixel 8 Pro AVD; installDebug; visual smoke pass
- [x] **T10** installDebug on physical phone; visual smoke pass
- [x] **T11** Commit sources + wrapper + docs; ensure `local.properties` untracked

---

## 9. Day-to-day commands

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
adb shell am start -n com.ukrailtracker.app/.MainActivity
./gradlew :app:uninstallDebug
./gradlew clean :app:assembleDebug
adb logcat | grep -i ukrailtracker
```

---

## 10. Acceptance criteria

- [ ] Toolchain on `PATH` (`java`, `sdkmanager`, `adb`, `emulator`)
- [ ] Compose app `com.ukrailtracker.app` with forced neon dark theme
- [ ] `assembleDebug` green
- [ ] Pixel 8 Pro emulator shows neon Hello World
- [ ] Physical phone shows neon Hello World
- [ ] Workflow works without Android Studio

---

## 11. Explicitly out of scope

- Release signing / Play Store
- CI
- Consuming `stations.xml` or live rail APIs
- Cards, charts, navigation, multi-screen UX
