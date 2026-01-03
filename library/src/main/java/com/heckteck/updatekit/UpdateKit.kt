package com.heckteck.updatekit

import android.app.Application
import com.heckteck.updatekit.data.AppUpdateRepositoryImpl
import com.heckteck.updatekit.data.InAppUpdateManager
import com.heckteck.updatekit.ui.AppUpdateViewModel
import timber.log.Timber

/**
 * Main entry point for UpdateKit.
 *
 * UpdateKit provides a complete, **DI-agnostic** solution for handling Google Play In-App Updates.
 * No dependency injection framework required!
 *
 * ## Quick Start (3 lines of code!)
 *
 * ### 1. Initialize in Application class
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *
 *         val versionProvider = StaticUpdateVersionProvider(
 *             currentVersionCode = BuildConfig.VERSION_CODE,
 *             forceUpdateBelowVersion = 10,
 *             recommendedUpdateBelowVersion = 15
 *         )
 *
 *         UpdateKit.initialize(
 *             application = this,
 *             versionProvider = versionProvider
 *         )
 *     }
 * }
 * ```
 *
 * ### 2. Initialize in MainActivity
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         // Initialize update manager for this activity
 *         UpdateKit.getManager().initialize(this)
 *
 *         setContent {
 *             AppUpdateDialog(packageName = BuildConfig.APPLICATION_ID)
 *         }
 *     }
 *
 *     override fun onResume() {
 *         super.onResume()
 *         UpdateKit.getManager().checkAndRetriggerImmediateUpdateIfNeeded()
 *     }
 *
 *     override fun onDestroy() {
 *         super.onDestroy()
 *         UpdateKit.getManager().cleanup()
 *     }
 * }
 * ```
 *
 * That's it! No DI setup needed! 🎉
 *
 * ## Features
 *
 * - ✅ **DI-Agnostic**: No dependency injection framework required
 * - ✅ **Source-agnostic**: Fetch version requirements from anywhere
 * - ✅ **Minimal API**: Just 2 method calls to set up
 * - ✅ **Flexible & Immediate updates**: Supports both update types
 * - ✅ **Session tracking**: Avoids showing flexible updates multiple times
 * - ✅ **Material3 UI**: Beautiful, ready-to-use dialogs
 * - ✅ **Zero boilerplate**: Works with Koin, Dagger, Hilt, or manual DI
 */
object UpdateKit {

    private var updateManager: InAppUpdateManager? = null
    private var viewModel: AppUpdateViewModel? = null
    private var application: Application? = null
    private var config: Config = Config()

    /**
     * Initialize UpdateKit.
     *
     * Call this once in your Application class's onCreate() method.
     *
     * @param application The Application instance
     * @param versionProvider Provider for app version and update thresholds
     * @param config Optional configuration for UpdateKit
     *
     * @see UpdateVersionProvider
     * @see Config
     */
    fun initialize(
        application: Application,
        versionProvider: UpdateVersionProvider,
        config: Config = Config()
    ) {
        if (updateManager != null) {
            Timber.w("⚠️ UpdateKit is already initialized")
            return
        }

        Timber.d("🚀 Initializing UpdateKit")
        Timber.d("   Version Provider: ${versionProvider::class.simpleName}")
        Timber.d("   Debug Logging: ${config.enableDebugLogging}")
        Timber.d("   Cooldown Minutes: ${config.updateCheckCooldownMinutes}")

        this.application = application
        this.config = config

        // Create dependencies manually (no DI framework required!)
        val repository = AppUpdateRepositoryImpl(versionProvider)
        updateManager = InAppUpdateManager(repository)
        viewModel = AppUpdateViewModel(updateManager!!)

        Timber.d("✅ UpdateKit initialized successfully")
    }

    /**
     * Get the update manager instance.
     *
     * Use this to control updates in your Activity.
     *
     * @return The InAppUpdateManager instance
     * @throws IllegalStateException if UpdateKit is not initialized
     */
    fun getManager(): InAppUpdateManager {
        return updateManager ?: error("UpdateKit not initialized. Call UpdateKit.initialize() first in your Application class.")
    }

    /**
     * Get the ViewModel instance for update UI.
     *
     * Used internally by AppUpdateDialog. Apps typically don't need to call this.
     *
     * @return The AppUpdateViewModel instance
     * @throws IllegalStateException if UpdateKit is not initialized
     */
    internal fun getViewModel(): AppUpdateViewModel {
        return viewModel ?: error("UpdateKit not initialized. Call UpdateKit.initialize() first in your Application class.")
    }

    /**
     * Get the current configuration.
     */
    fun getConfig(): Config {
        return config
    }

    /**
     * Get the Application instance.
     */
    internal fun getApplication(): Application {
        return application ?: error("UpdateKit not initialized.")
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
