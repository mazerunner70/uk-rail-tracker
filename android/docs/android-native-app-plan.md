# Android Native App Plan

Step-by-step runbook for scaffolding a Kotlin + Jetpack Compose Hello World app under `android/`, with a CLI-first build and deploy workflow for emulator and physical phone.

## Goals

- Create a native Android project in this repo’s `android/` directory.
- Ship a single-screen Hello World app as the first milestone.
- Support a full local dev loop: install toolchain → build → run on emulator → deploy to a phone.
- Prefer CLI tools (`./gradlew`, `adb`, `sdkmanager`, `avdmanager`) so the process works without Android Studio. Studio remains optional.

## Locked decisions

| Item | Choice |
|------|--------|
| Language / UI | Kotlin + Jetpack Compose |
| Visual theme | Neon data-viz (dark canvas, electric cyan / magenta accents) |
| Emulator device | Pixel 8 Pro (`pixel_8_pro` AVD) |
| Build system | Gradle Kotlin DSL + Android Gradle Plugin |
| Application ID | `com.ukrailtracker.app` |
| Min SDK | 26 |
| Compile / target SDK | 35 |
| Build type for now | Debug only (`assembleDebug` / `installDebug`) |
| IDE | Optional; CLI is the source of truth |

## Out of scope (for later)

- Release / Play Store signing
- CI pipelines
- App features beyond Hello World
- Consuming repo-root `stations.xml`

---

## 1. Toolchain setup (Ubuntu)

This machine may start with no JDK and no Android SDK. Complete this section before scaffolding the project.

### 1.1 Install JDK 17

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
java -version   # expect 17.x
javac -version
```

Set `JAVA_HOME` (path may vary; check with `dirname $(dirname $(readlink -f $(which javac)))`):

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

### 1.2 Install Android command-line tools

```bash
mkdir -p ~/Android/Sdk/cmdline-tools
cd /tmp
curl -fsSLO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
# sdkmanager expects cmdline-tools/latest/...
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest
```

If the zip URL version is outdated, fetch the current “Command line tools only” Linux package from [Android Studio download page](https://developer.android.com/studio#command-tools) and repeat the extract/move steps.

### 1.3 Environment variables

Add to `~/.bashrc` (or equivalent), then `source` it:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin"
```

Verify:

```bash
echo "$ANDROID_HOME"
sdkmanager --version
```

### 1.4 Install SDK packages

```bash
yes | sdkmanager --licenses

sdkmanager \
  "platform-tools" \
  "platforms;android-35" \
  "build-tools;35.0.0" \
  "emulator" \
  "system-images;android-35;google_apis;x86_64"
```

On machines without KVM / x86_64 virtualization, use an ARM system image instead (slower), e.g. `system-images;android-35;google_apis;arm64-v8a`.

Verify:

```bash
adb version
emulator -version
```

### 1.5 Optional: Android Studio

Install Android Studio if you want a GUI debugger/layout inspector. Point it at the same `ANDROID_HOME` (`~/Android/Sdk`) so CLI and IDE share one SDK.

---

## 2. Project scaffold

Create the app as a standard single-module Compose project rooted at `android/`.

### 2.1 Layout to create

```text
android/
  docs/
    android-native-app-plan.md   # this file
  .gitignore
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew
  gradlew.bat
  gradle/
    wrapper/
      gradle-wrapper.properties
      gradle-wrapper.jar
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/ukrailtracker/app/MainActivity.kt
    src/main/java/com/ukrailtracker/app/ui/theme/Color.kt
    src/main/java/com/ukrailtracker/app/ui/theme/Theme.kt
    src/main/java/com/ukrailtracker/app/ui/theme/Type.kt
    src/main/res/values/strings.xml
    src/main/res/values/themes.xml
    src/main/res/xml/backup_rules.xml   # if required by template
    src/main/res/xml/data_extraction_rules.xml
```

### 2.2 Gradle configuration (outline)

**`settings.gradle.kts`**

- `rootProject.name = "uk-rail-tracker"`
- `include(":app")`
- Plugin / dependency repos: `google()`, `mavenCentral()`

**Root `build.gradle.kts`**

- Declare Android and Kotlin plugin versions (apply `false` at root)

**`app/build.gradle.kts`**

- Plugin: `com.android.application`, `org.jetbrains.kotlin.android`, Compose compiler plugin as required by the AGP/Kotlin pair
- `namespace` / `applicationId`: `com.ukrailtracker.app`
- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`
- Enable Compose (`buildFeatures { compose = true }`)
- Dependencies: Compose BOM, `activity-compose`, Material 3, UI tooling (debug)

**`gradle.properties`**

- `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`
- `android.useAndroidX=true`
- Kotlin code style / nonTransitiveRClass as desired

**Gradle Wrapper**

- Commit the wrapper scripts and `gradle-wrapper.properties` so builds do not require a global Gradle install.
- Generate with an installed Gradle once if needed: `gradle wrapper --gradle-version <compatible>` then use `./gradlew` thereafter.

### 2.3 Application code (Hello World + neon theme)

**Neon palette** (Compose `Color.kt` / Material 3 `darkColorScheme`) — aimed at live data presentation, not a default Material light theme:

| Role | Hex | Use |
|------|-----|-----|
| Background | `#0B0F1A` | Near-black canvas |
| Surface | `#121826` | Panels / future cards |
| Primary (cyan) | `#00F0FF` | Brand accent, primary text glow |
| Secondary (magenta) | `#FF2BD6` | Highlights, secondary emphasis |
| On-background | `#E8F7FF` | Body text |
| Outline / grid hint | `#1E3A5F` | Subtle structure for later charts |

Keep the Hello World screen sparse: full-bleed dark background, brand-level app name, one neon “Hello World” line, short supporting subtitle. No cards, pills, or dashboard chrome yet—those come with real data screens later. Soft cyan glow on the title (e.g. `shadow` / layered text) is enough motion/atmosphere for v1.

**`Theme.kt`**

- Force dark neon `colorScheme` (do not follow system light mode for this app shell).
- Wire typography in `Type.kt` toward a distinctive display face if bundled later; for Hello World, use Compose defaults with larger weight/size on the hero line.

**`MainActivity.kt`**

- `ComponentActivity` with `setContent { UkRailTrackerTheme { ... } }`
- Centered column: app name in cyan, “Hello World” as the hero line, one muted supporting sentence

**`AndroidManifest.xml`**

- Single launcher activity: `.MainActivity`
- App label from `@string/app_name` (e.g. “UK Rail Tracker”)
- Status/nav bars aligned with the dark neon theme (`themes.xml` / `enableEdgeToEdge` as appropriate)

**Resources**

- Minimal `strings.xml` / `themes.xml` for Compose + system bars (dark window background)

### 2.4 `.gitignore`

Ignore at least:

- `.gradle/`, `build/`, `local.properties`
- `*.iml`, `.idea/` (if not sharing IDE config)
- `.cxx/`, captures, keystores (when added later)

`local.properties` should point at the SDK (`sdk.dir=...`) and stay machine-local; Gradle usually auto-creates it when `ANDROID_HOME` is set, or create it manually:

```properties
sdk.dir=/home/<user>/Android/Sdk
```

### 2.5 First build (no device yet)

From `android/`:

```bash
./gradlew :app:assembleDebug
```

Expected artifact: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 3. Emulator workflow

### 3.1 Create an AVD

```bash
avdmanager create avd \
  --name ukrail_pixel8pro_api35 \
  --package "system-images;android-35;google_apis;x86_64" \
  --device "pixel_8_pro" \
  --force
```

Confirm `pixel_8_pro` is listed (`avdmanager list device | grep -i pixel_8_pro`). If the device id is missing on an older SDK package, install/update emulator images or use the closest Pixel 8 Pro profile id shown by `avdmanager list device`.

List AVDs:

```bash
emulator -list-avds
```

### 3.2 Start the emulator

```bash
emulator -avd ukrail_pixel8pro_api35 -netdelay none -netspeed full &
adb wait-for-device
# Wait until boot completes
adb shell getprop sys.boot_completed   # expect "1"
```

If `sys.boot_completed` is empty, sleep and retry until it returns `1`.

### 3.3 Install and launch

From `android/`:

```bash
./gradlew :app:installDebug
adb shell am start -n com.ukrailtracker.app/.MainActivity
```

### 3.4 Smoke check

- Pixel 8 Pro emulator shows the app UI.
- Dark neon canvas with cyan/magenta accents; screen displays **Hello World**.
- No crash dialog; `adb logcat` clean of fatal exceptions for the app PID.

Stop emulator when done: close the window, or `adb -s emulator-5554 emu kill`.

---

## 4. Physical phone workflow

### 4.1 Enable debugging on the phone

1. Settings → About phone → tap **Build number** seven times (Developer options).
2. Settings → Developer options → enable **USB debugging**.
3. Connect via a data-capable USB cable.

### 4.2 Authorize and verify

```bash
adb devices
```

- First connect: accept the RSA fingerprint prompt on the phone.
- Device should appear as `device` (not `unauthorized` or `offline`).

If the device does not appear:

- Try another cable/port.
- Install OEM USB drivers if required by the manufacturer.
- Toggle USB mode to File transfer / MTP (some phones hide adb in “charging only”).

### 4.3 Install and launch

With only the phone connected (or select it with `-s <serial>`):

```bash
./gradlew :app:installDebug
adb shell am start -n com.ukrailtracker.app/.MainActivity
```

### 4.4 Smoke check

- App installs and opens on the phone.
- Neon Hello World screen is visible (dark background, cyan hero text).
- Uninstall if needed: `./gradlew :app:uninstallDebug` or `adb uninstall com.ukrailtracker.app`.

### 4.5 Optional later: wireless debugging

Android 11+ supports wireless debugging from Developer options. Pair with `adb pair <ip:port>` then `adb connect <ip:port>`. Not required for the Hello World milestone.

---

## 5. Day-to-day commands

Run from `android/`:

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Install on the default connected device/emulator
./gradlew :app:installDebug

# Launch
adb shell am start -n com.ukrailtracker.app/.MainActivity

# Uninstall
./gradlew :app:uninstallDebug

# Clean rebuild
./gradlew clean :app:assembleDebug

# Logs while reproducing an issue
adb logcat | grep -i ukrailtracker
```

Multiple devices:

```bash
adb devices
adb -s <serial> shell am start -n com.ukrailtracker.app/.MainActivity
```

---

## 6. Implementation order

Execute in this sequence when building the project (follow-up work after this plan):

1. Install JDK 17 and Android SDK packages; set env vars; accept licenses.
2. Scaffold Gradle project + neon Compose theme + `MainActivity` Hello World under `android/`.
3. Confirm `./gradlew :app:assembleDebug` succeeds.
4. Create Pixel 8 Pro AVD (`ukrail_pixel8pro_api35`); install and launch on emulator; verify neon Hello World.
5. Enable USB debugging; install and launch on phone; verify neon Hello World.
6. Commit project sources + wrapper + this doc; keep `local.properties` untracked.

---

## 7. Acceptance criteria

- [ ] JDK 17 and Android SDK (`platform-tools`, `android-35`, build-tools, emulator, system image) installed; `sdkmanager` / `adb` on `PATH`.
- [ ] `android/` contains a Compose app with application id `com.ukrailtracker.app` and neon dark theme.
- [ ] `./gradlew :app:assembleDebug` completes successfully.
- [ ] App installs and launches on a Pixel 8 Pro emulator; neon Hello World is visible.
- [ ] App installs and launches on a physical phone; neon Hello World is visible.
- [ ] Debug workflow documented and usable without Android Studio.

---

## 8. Future follow-ups (not part of Hello World)

- Product features (live rail data, stations from `stations.xml`, maps, etc.).
- Release signing and Play / sideload release builds.
- CI (e.g. GitHub Actions) running `assembleDebug` on PRs.
- Instrumented / UI tests for the main screen.
