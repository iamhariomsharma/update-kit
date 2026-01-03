package com.heckteck.updatekit

import android.app.Application
import com.heckteck.updatekit.data.AppUpdateRepositoryImpl
import com.heckteck.updatekit.data.IAppUpdateRepository
import com.heckteck.updatekit.data.InAppUpdateManager
import com.heckteck.updatekit.ui.AppUpdateViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import timber.log.Timber

/**
 * Main entry point for UpdateKit.
 *
 * UpdateKit provides a complete solution for handling Google Play In-App Updates
 * with a source-agnostic design. You can fetch version requirements from:
 * - Firebase Remote Config
 * - REST API
 * - GraphQL
 * - Local database
 * - Hardcoded values (for testing)
 * - Any custom source
 *
 * ## Quick Start
 *
 * ### 1. Choose or create an [UpdateVersionProvider]
 * ```kotlin
 * // Option A: Firebase Remote Config
 * val firebaseConfig = Firebase.remoteConfig
 * val versionProvider = FirebaseUpdateVersionProvider(
 *     firebaseRemoteConfig = firebaseConfig,
 *     currentVersionCode = BuildConfig.VERSION_CODE
 * )
 *
 * // Option B: Static values (testing)
 * val versionProvider = StaticUpdateVersionProvider(
 *     currentVersionCode = BuildConfig.VERSION_CODE,
 *     forceUpdateBelowVersion = 10,
 *     recommendedUpdateBelowVersion = 15
 * )
 *
 * // Option C: Your own implementation
 * class MyApiVersionProvider : UpdateVersionProvider { ... }
 * ```
 *
 * ### 2. Initialize in your Application class
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *
 *         UpdateKit.initialize(
 *             application = this,
 *             versionProvider = versionProvider,
 *             config = Config(
 *                 enableDebugLogging = BuildConfig.DEBUG
 *             )
 *         )
 *     }
 * }
 * ```
 *
 * ### 3. Initialize in your MainActivity
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private val updateManager: InAppUpdateManager by inject()
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         updateManager.initialize(this)
 *
 *         setContent {
 *             AppUpdateDialog(packageName = BuildConfig.APPLICATION_ID)
 *         }
 *     }
 *
 *     override fun onResume() {
 *         super.onResume()
 *         updateManager.checkAndRetriggerImmediateUpdateIfNeeded()
 *     }
 *
 *     override fun onDestroy() {
 *         super.onDestroy()
 *         updateManager.cleanup()
 *     }
 * }
 * ```
 *
 * ## Features
 *
 * - ✅ **Source-agnostic**: Fetch version requirements from anywhere
 * - ✅ **Flexible & Immediate updates**: Supports both update types
 * - ✅ **Session tracking**: Avoids showing flexible updates multiple times
 * - ✅ **Cooldown periods**: Prevents update prompts from being annoying
 * - ✅ **Installation monitoring**: Tracks update progress with timeout handling
 * - ✅ **Material3 UI**: Beautiful, ready-to-use dialogs
 * - ✅ **Stalled update recovery**: Resumes interrupted updates
 * - ✅ **Play Store fallback**: Opens Play Store if in-app update fails
 *
 * ## Update Types
 *
 * - **IMMEDIATE**: User must update before continuing to use the app
 * - **FLEXIBLE**: User can update in the background while using the app
 * - **NO_UPDATE**: App is up to date
 *
 * The update type is determined by comparing the current app version with
 * the thresholds provided by your [UpdateVersionProvider].
 */
object UpdateKit {

    /**
     * Create a Koin module for UpdateKit.
     *
     * **Use this if your app already uses Koin.** Include this module in your app's
     * existing `startKoin {}` block.
     *
     * ## Example:
     * ```kotlin
     * class MyApp : Application() {
     *     override fun onCreate() {
     *         super.onCreate()
     *
     *         val versionProvider = FirebaseUpdateVersionProvider(...)
     *
     *         startKoin {
     *             androidContext(this@MyApp)
     *             modules(
     *                 myAppModule,  // Your app's module
     *                 UpdateKit.createModule(versionProvider)  // UpdateKit's module
     *             )
     *         }
     *     }
     * }
     * ```
     *
     * @param versionProvider Provider for app version and update thresholds
     * @param config Optional configuration for UpdateKit
     * @return Koin Module containing all UpdateKit dependencies
     */
    fun createModule(
        versionProvider: UpdateVersionProvider,
        config: Config = Config()
    ) = module {
        Timber.d("🚀 Creating UpdateKit module")
        Timber.d("   Version Provider: ${versionProvider::class.simpleName}")
        Timber.d("   Debug Logging: ${config.enableDebugLogging}")

        // Provide the version provider as a singleton
        single { versionProvider }

        // Provide the config
        single { config }

        // Repository
        singleOf(::AppUpdateRepositoryImpl) bind IAppUpdateRepository::class

        // Update Manager
        singleOf(::InAppUpdateManager)

        // ViewModel
        viewModelOf(::AppUpdateViewModel)
    }

    /**
     * Initialize UpdateKit (for apps that don't use Koin yet).
     *
     * **Use this only if your app doesn't use Koin.** If your app already uses Koin,
     * use [createModule] instead and include it in your existing `startKoin {}` block.
     *
     * @param application The Application instance
     * @param versionProvider Provider for app version and update thresholds
     * @param config Optional configuration for the library
     *
     * @see createModule for apps that already use Koin
     * @see UpdateVersionProvider
     * @see Config
     */
    fun initialize(
        application: Application,
        versionProvider: UpdateVersionProvider,
        config: Config = Config()
    ) {
        Timber.d("🚀 Initializing UpdateKit (standalone mode)")

        try {
            startKoin {
                androidContext(application)
                modules(
                    createModule(versionProvider, config)
                )
            }
            Timber.d("✅ UpdateKit initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to initialize UpdateKit")
            throw e
        }
    }

    /**
     * Configuration options for UpdateKit.
     *
     * @property enableDebugLogging Enable detailed logging (useful for debugging)
     * @property updateCheckCooldownMinutes Cooldown period in minutes after dismissing flexible update
     */
    data class Config(
        val enableDebugLogging: Boolean = false,
        val updateCheckCooldownMinutes: Int = 30
    )
}
