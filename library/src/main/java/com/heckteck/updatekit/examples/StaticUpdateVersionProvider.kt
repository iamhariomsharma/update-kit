package com.heckteck.updatekit.examples

import com.heckteck.updatekit.UpdateVersionProvider
import com.heckteck.updatekit.VersionThresholds
import timber.log.Timber

/**
 * Example implementation of [UpdateVersionProvider] with static/hardcoded values.
 *
 * This provider is useful for:
 * - Testing the update flow without a backend
 * - Development and debugging
 * - Apps that don't need server-side update control
 * - Quick prototyping
 *
 * ## Usage Example:
 * ```kotlin
 * // Force update for versions below 10, recommend update for versions below 15
 * val versionProvider = StaticUpdateVersionProvider(
 *     currentVersionCode = BuildConfig.VERSION_CODE,
 *     forceUpdateBelowVersion = 10,
 *     recommendedUpdateBelowVersion = 15
 * )
 *
 * InAppUpdateLibrary.initialize(
 *     application = this,
 *     versionProvider = versionProvider
 * )
 * ```
 *
 * ## Testing Different Scenarios:
 * ```kotlin
 * // Test force update (if current version is 8):
 * StaticUpdateVersionProvider(
 *     currentVersionCode = 8,
 *     forceUpdateBelowVersion = 10,  // Will trigger IMMEDIATE update
 *     recommendedUpdateBelowVersion = 15
 * )
 *
 * // Test flexible update (if current version is 12):
 * StaticUpdateVersionProvider(
 *     currentVersionCode = 12,
 *     forceUpdateBelowVersion = 10,
 *     recommendedUpdateBelowVersion = 15  // Will trigger FLEXIBLE update
 * )
 *
 * // Test no update (if current version is 20):
 * StaticUpdateVersionProvider(
 *     currentVersionCode = 20,
 *     forceUpdateBelowVersion = 10,
 *     recommendedUpdateBelowVersion = 15  // No update needed
 * )
 * ```
 *
 * @property currentVersionCode Current app version code
 * @property forceUpdateBelowVersion Apps below this version must update immediately
 * @property recommendedUpdateBelowVersion Apps below this version should update (flexible)
 */
class StaticUpdateVersionProvider(
    private val currentVersionCode: Int,
    private val forceUpdateBelowVersion: Long,
    private val recommendedUpdateBelowVersion: Long
) : UpdateVersionProvider {

    init {
        Timber.d("StaticUpdateVersionProvider initialized")
        Timber.d("  currentVersionCode: $currentVersionCode")
        Timber.d("  forceUpdateBelowVersion: $forceUpdateBelowVersion")
        Timber.d("  recommendedUpdateBelowVersion: $recommendedUpdateBelowVersion")

        // Log what update type will be triggered
        val updateType = when {
            currentVersionCode < forceUpdateBelowVersion -> "IMMEDIATE (force update)"
            currentVersionCode < recommendedUpdateBelowVersion -> "FLEXIBLE (recommended update)"
            else -> "NO_UPDATE (app is up to date)"
        }
        Timber.d("  Expected update type: $updateType")
    }

    override fun getCurrentVersionCode(): Int = currentVersionCode

    override suspend fun getVersionThresholds(): VersionThresholds {
        Timber.d("Returning static version thresholds")
        return VersionThresholds(
            forceUpdateBelowVersion = forceUpdateBelowVersion,
            recommendedUpdateBelowVersion = recommendedUpdateBelowVersion
        )
    }
}

/**
 * Helper to create a provider that never requires updates.
 * Useful for testing or temporarily disabling updates.
 *
 * ## Usage:
 * ```kotlin
 * val versionProvider = noUpdateRequired(BuildConfig.VERSION_CODE)
 * ```
 */
fun noUpdateRequired(currentVersionCode: Int): StaticUpdateVersionProvider {
    return StaticUpdateVersionProvider(
        currentVersionCode = currentVersionCode,
        forceUpdateBelowVersion = 1,  // Only force update for version 0 (never)
        recommendedUpdateBelowVersion = 1  // Only recommend update for version 0 (never)
    )
}

/**
 * Helper to create a provider that always forces update.
 * Useful for testing the force update flow.
 *
 * ## Usage:
 * ```kotlin
 * val versionProvider = alwaysForceUpdate(BuildConfig.VERSION_CODE)
 * ```
 */
fun alwaysForceUpdate(currentVersionCode: Int): StaticUpdateVersionProvider {
    return StaticUpdateVersionProvider(
        currentVersionCode = currentVersionCode,
        forceUpdateBelowVersion = Long.MAX_VALUE,  // Force update for all versions
        recommendedUpdateBelowVersion = Long.MAX_VALUE
    )
}

/**
 * Helper to create a provider that always recommends update (flexible).
 * Useful for testing the flexible update flow.
 *
 * ## Usage:
 * ```kotlin
 * val versionProvider = alwaysRecommendUpdate(BuildConfig.VERSION_CODE)
 * ```
 */
fun alwaysRecommendUpdate(currentVersionCode: Int): StaticUpdateVersionProvider {
    return StaticUpdateVersionProvider(
        currentVersionCode = currentVersionCode,
        forceUpdateBelowVersion = 1,  // Don't force update
        recommendedUpdateBelowVersion = Long.MAX_VALUE  // Recommend update for all versions
    )
}
