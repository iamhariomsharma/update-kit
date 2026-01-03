package com.heckteck.updatekit.examples

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.heckteck.updatekit.UpdateVersionProvider
import com.heckteck.updatekit.VersionThresholds
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * Example implementation of [UpdateVersionProvider] using Firebase Remote Config.
 *
 * This provider fetches version thresholds from Firebase Remote Config, allowing
 * server-side control of app update requirements.
 *
 * ## Usage Example:
 * ```kotlin
 * // In your Application class
 * val firebaseConfig = Firebase.remoteConfig
 * firebaseConfig.setConfigSettingsAsync(
 *     FirebaseRemoteConfigSettings.Builder()
 *         .setMinimumFetchIntervalInSeconds(3600)
 *         .build()
 * )
 *
 * val versionProvider = FirebaseUpdateVersionProvider(
 *     firebaseRemoteConfig = firebaseConfig,
 *     currentVersionCode = BuildConfig.VERSION_CODE,
 *     forceUpdateKey = "force_update_below_version",
 *     recommendedUpdateKey = "recommended_update_below_version"
 * )
 *
 * InAppUpdateLibrary.initialize(
 *     application = this,
 *     versionProvider = versionProvider
 * )
 * ```
 *
 * ## Firebase Console Setup:
 * Add these keys to your Firebase Remote Config:
 * - `force_update_below_version` (Number) - Apps below this version must update immediately
 * - `recommended_update_below_version` (Number) - Apps below this version should update (flexible)
 *
 * @property firebaseRemoteConfig Firebase Remote Config instance
 * @property currentVersionCode Current app version code
 * @property forceUpdateKey Remote Config key for force update threshold
 * @property recommendedUpdateKey Remote Config key for recommended update threshold
 * @property fetchTimeoutSeconds Timeout for fetching remote config (default: 10 seconds)
 */
class FirebaseUpdateVersionProvider(
    private val firebaseRemoteConfig: FirebaseRemoteConfig,
    private val currentVersionCode: Int,
    private val forceUpdateKey: String = "force_update_below_version",
    private val recommendedUpdateKey: String = "recommended_update_below_version",
    private val fetchTimeoutSeconds: Long = 10
) : UpdateVersionProvider {

    init {
        // Set up default values to ensure the app works even if fetch fails
        val defaults = mapOf(
            forceUpdateKey to 1L,  // By default, no force update
            recommendedUpdateKey to 1L  // By default, no recommended update
        )
        firebaseRemoteConfig.setDefaultsAsync(defaults)

        Timber.d("FirebaseUpdateVersionProvider initialized")
        Timber.d("  forceUpdateKey: $forceUpdateKey")
        Timber.d("  recommendedUpdateKey: $recommendedUpdateKey")
    }

    override fun getCurrentVersionCode(): Int = currentVersionCode

    override suspend fun getVersionThresholds(): VersionThresholds? {
        return try {
            Timber.d("Fetching version thresholds from Firebase Remote Config...")

            // Fetch and activate the remote config
            val fetchResult = firebaseRemoteConfig.fetchAndActivate().await()
            Timber.d("Firebase fetch result: $fetchResult")

            // Get the version thresholds
            val forceUpdateBelowVersion = firebaseRemoteConfig.getLong(forceUpdateKey)
            val recommendedUpdateBelowVersion = firebaseRemoteConfig.getLong(recommendedUpdateKey)

            Timber.d("✅ Firebase Remote Config fetched successfully")
            Timber.d("   $forceUpdateKey = $forceUpdateBelowVersion")
            Timber.d("   $recommendedUpdateKey = $recommendedUpdateBelowVersion")

            VersionThresholds(
                forceUpdateBelowVersion = forceUpdateBelowVersion,
                recommendedUpdateBelowVersion = recommendedUpdateBelowVersion
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to fetch Firebase Remote Config")
            Timber.w("Falling back to default values")

            // Return defaults if fetch fails
            VersionThresholds(
                forceUpdateBelowVersion = firebaseRemoteConfig.getLong(forceUpdateKey),
                recommendedUpdateBelowVersion = firebaseRemoteConfig.getLong(recommendedUpdateKey)
            )
        }
    }
}

/**
 * Helper function to create and configure Firebase Remote Config for updates.
 *
 * ## Usage:
 * ```kotlin
 * val firebaseConfig = createFirebaseRemoteConfigForUpdates(
 *     fetchIntervalSeconds = 3600,  // Fetch at most once per hour
 *     defaultForceUpdateBelowVersion = 1,
 *     defaultRecommendedUpdateBelowVersion = 1
 * )
 *
 * val versionProvider = FirebaseUpdateVersionProvider(
 *     firebaseRemoteConfig = firebaseConfig,
 *     currentVersionCode = BuildConfig.VERSION_CODE
 * )
 * ```
 *
 * @param fetchIntervalSeconds Minimum interval between fetches (default: 1 hour)
 * @param defaultForceUpdateBelowVersion Default value if remote fetch fails
 * @param defaultRecommendedUpdateBelowVersion Default value if remote fetch fails
 * @return Configured FirebaseRemoteConfig instance
 */
fun createFirebaseRemoteConfigForUpdates(
    fetchIntervalSeconds: Long = 3600,
    defaultForceUpdateBelowVersion: Long = 1,
    defaultRecommendedUpdateBelowVersion: Long = 1
): FirebaseRemoteConfig {
    val remoteConfig = FirebaseRemoteConfig.getInstance()

    val configSettings = FirebaseRemoteConfigSettings.Builder()
        .setMinimumFetchIntervalInSeconds(fetchIntervalSeconds)
        .build()

    remoteConfig.setConfigSettingsAsync(configSettings)

    val defaults = mapOf(
        "force_update_below_version" to defaultForceUpdateBelowVersion,
        "recommended_update_below_version" to defaultRecommendedUpdateBelowVersion
    )
    remoteConfig.setDefaultsAsync(defaults)

    return remoteConfig
}
