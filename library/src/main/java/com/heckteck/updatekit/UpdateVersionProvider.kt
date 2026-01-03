package com.heckteck.updatekit

/**
 * Interface for providing app version information and update thresholds.
 *
 * Implementations can fetch version thresholds from any source:
 * - Firebase Remote Config
 * - REST API
 * - GraphQL
 * - Local database
 * - Hardcoded values (for testing)
 * - Any custom source
 *
 * This design follows the Dependency Inversion Principle, allowing the library
 * to remain agnostic about where version information comes from.
 */
interface UpdateVersionProvider {

    /**
     * Get the current app version code.
     *
     * @return The current version code (e.g., BuildConfig.VERSION_CODE)
     */
    fun getCurrentVersionCode(): Int

    /**
     * Get version thresholds from any configured source.
     *
     * The implementation should fetch the minimum version codes for:
     * - Force update (immediate update required)
     * - Recommended update (flexible update suggested)
     *
     * @return [VersionThresholds] if successfully fetched, null if unable to fetch
     *         (e.g., network error, not initialized, service unavailable)
     */
    suspend fun getVersionThresholds(): VersionThresholds?
}

/**
 * Data class representing version thresholds for determining update requirements.
 *
 * @property forceUpdateBelowVersion Apps with version code below this must update immediately
 * @property recommendedUpdateBelowVersion Apps with version code below this should update (flexible)
 */
data class VersionThresholds(
    val forceUpdateBelowVersion: Long,
    val recommendedUpdateBelowVersion: Long
)
