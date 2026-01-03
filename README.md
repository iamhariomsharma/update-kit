# UpdateKit

[![](https://jitpack.io/v/iamhariomsharma/update-kit.svg)](https://jitpack.io/#iamhariomsharma/update-kit)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Android API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=26)

A source-agnostic Android library for handling Google Play In-App Updates with Material3 UI.

## ✨ Features

- ✅ **Source-Agnostic Design** - Fetch version requirements from Firebase, REST API, GraphQL, or any custom source
- ✅ **Flexible & Immediate Updates** - Supports both update types with smart session tracking
- ✅ **Material3 UI** - Beautiful, ready-to-use dialogs
- ✅ **Zero Boilerplate** - Simple integration in 3 steps
- ✅ **Cooldown Periods** - Prevents annoying repeated update prompts
- ✅ **Installation Monitoring** - Tracks update progress with timeout handling
- ✅ **Stalled Update Recovery** - Automatically resumes interrupted updates
- ✅ **Play Store Fallback** - Opens Play Store if in-app update fails

## 📦 Installation

### Step 1: Add JitPack Repository

Add JitPack to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }  // Add this
    }
}
```

### Step 2: Add Dependency

Add UpdateKit to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.iamhariomsharma:update-kit:1.0.0")
}
```

## 🚀 Quick Start

### 1. Choose a Version Provider

UpdateKit is source-agnostic. Choose how you want to provide version thresholds:

#### Option A: Firebase Remote Config (Recommended)

```kotlin
val versionProvider = FirebaseUpdateVersionProvider(
    firebaseRemoteConfig = Firebase.remoteConfig,
    currentVersionCode = BuildConfig.VERSION_CODE,
    forceUpdateKey = "force_update_below_version",
    recommendedUpdateKey = "recommended_update_below_version"
)
```

#### Option B: Static Values (Testing)

```kotlin
val versionProvider = StaticUpdateVersionProvider(
    currentVersionCode = BuildConfig.VERSION_CODE,
    forceUpdateBelowVersion = 10,      // Force update below version 10
    recommendedUpdateBelowVersion = 15  // Recommend update below version 15
)
```

#### Option C: Custom Implementation

```kotlin
class MyApiVersionProvider(
    private val apiService: YourApiService,
    private val currentVersionCode: Int
) : UpdateVersionProvider {
    override fun getCurrentVersionCode() = currentVersionCode

    override suspend fun getVersionThresholds(): VersionThresholds? {
        val response = apiService.getAppConfig()
        return VersionThresholds(
            forceUpdateBelowVersion = response.minVersion,
            recommendedUpdateBelowVersion = response.recommendedVersion
        )
    }
}
```

### 2. Initialize in Application Class

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // If your app already uses Koin
        startKoin {
            androidContext(this@MyApp)
            modules(
                myAppModule,
                UpdateKit.createModule(versionProvider)  // Add UpdateKit module
            )
        }

        // If your app doesn't use Koin
        UpdateKit.initialize(
            application = this,
            versionProvider = versionProvider
        )
    }
}
```

### 3. Initialize in MainActivity

```kotlin
class MainActivity : ComponentActivity() {
    private val updateManager: InAppUpdateManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager.initialize(this)

        setContent {
            MyAppTheme {
                // Add the update dialog
                AppUpdateDialog(packageName = BuildConfig.APPLICATION_ID)

                // Your app content
                MyAppContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateManager.checkAndRetriggerImmediateUpdateIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateManager.cleanup()
    }
}
```

That's it! UpdateKit is now integrated. 🎉

## 📖 How It Works

```
┌─────────────────────────────────────────────────────────────┐
│                    Update Flow                               │
└─────────────────────────────────────────────────────────────┘

1. App starts
   └─ AppUpdateDialog checks for updates via LaunchedEffect

2. UpdateKit checks version thresholds
   ├─ Calls versionProvider.getVersionThresholds()
   ├─ Gets from Firebase/API/Static/Custom source
   └─ Compares with current version code

3. Determines update type
   ├─ Version < forceUpdateBelowVersion     → IMMEDIATE update
   ├─ Version < recommendedUpdateBelowVersion → FLEXIBLE update
   └─ Otherwise                             → NO_UPDATE

4. Triggers appropriate update flow
   ├─ IMMEDIATE: Shows Play Store update (or custom dialog if unsupported)
   ├─ FLEXIBLE: Shows Google Play update sheet
   └─ NO_UPDATE: No action
```

## 🎨 Update Types

### IMMEDIATE Update
- User **must** update before continuing
- Update sheet is non-dismissible
- Re-triggers if user tries to cancel
- Use for: Critical security fixes, breaking API changes

### FLEXIBLE Update
- User **can** update in background
- Shown once per session with 30-minute cooldown
- User can dismiss and continue using app
- Use for: Feature updates, minor improvements

## 🔧 Configuration

```kotlin
UpdateKit.Config(
    enableDebugLogging = BuildConfig.DEBUG,  // Verbose logs in debug mode
    updateCheckCooldownMinutes = 30          // Cooldown after dismissing flexible update
)
```

## 📱 Example Implementations Included

UpdateKit includes ready-to-use version provider examples:

1. **FirebaseUpdateVersionProvider** - For Firebase Remote Config
2. **StaticUpdateVersionProvider** - For hardcoded values/testing
   - Helper: `noUpdateRequired(versionCode)`
   - Helper: `alwaysForceUpdate(versionCode)`
   - Helper: `alwaysRecommendUpdate(versionCode)`

## 🛠️ Advanced Usage

### Manual Update Trigger

```kotlin
val updateManager: InAppUpdateManager by inject()

// Manually check for updates
lifecycleScope.launch {
    updateManager.checkForUpdate()
}

// Reset session tracking
updateManager.resetSessionTracking()

// Open Play Store directly
updateManager.openPlayStoreForUpdate(context, packageName)
```

### Observe Update State

```kotlin
val updateManager: InAppUpdateManager by inject()

lifecycleScope.launch {
    updateManager.updateState.collect { state ->
        when (state) {
            is UpdateState.UpdateAvailable -> { /* Show custom UI */ }
            is UpdateState.UpdateDownloaded -> { /* Ready to install */ }
            is UpdateState.UpdateFailed -> { /* Handle error */ }
            // ... other states
        }
    }
}
```

## 🧪 Testing

Use `StaticUpdateVersionProvider` to test different scenarios:

```kotlin
// Test force update
val provider = StaticUpdateVersionProvider(
    currentVersionCode = 5,
    forceUpdateBelowVersion = 10,      // Will trigger IMMEDIATE
    recommendedUpdateBelowVersion = 15
)

// Test flexible update
val provider = StaticUpdateVersionProvider(
    currentVersionCode = 12,
    forceUpdateBelowVersion = 10,
    recommendedUpdateBelowVersion = 15  // Will trigger FLEXIBLE
)

// Test no update
val provider = noUpdateRequired(BuildConfig.VERSION_CODE)
```

## 📋 Requirements

- **Min SDK:** 26 (Android 8.0)
- **Compile SDK:** 36+
- **Dependencies:**
  - Jetpack Compose
  - Material3
  - Koin (for DI)
  - Google Play In-App Update 2.1.0+

## 🔐 Firebase Setup (if using FirebaseUpdateVersionProvider)

Add these keys to your Firebase Remote Config:

| Key | Type | Description |
|-----|------|-------------|
| `force_update_below_version` | Number | Apps below this version must update immediately |
| `recommended_update_below_version` | Number | Apps below this version should update (flexible) |

**Example values:**
- `force_update_below_version` = 10 → Force update for versions 1-9
- `recommended_update_below_version` = 15 → Suggest update for versions 10-14

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

```
Copyright 2026 Hariom Sharma

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 💬 Questions?

Open an issue on GitHub or contact [@iamhariomsharma](https://github.com/iamhariomsharma)

---

**Made with ❤️ by [Hariom Sharma](https://github.com/iamhariomsharma)**
