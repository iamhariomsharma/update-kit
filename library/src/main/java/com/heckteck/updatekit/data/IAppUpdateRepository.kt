package com.heckteck.updatekit.data

/**
 * Repository interface for checking app updates.
 *
 * This interface abstracts the update checking logic, allowing different implementations
 * to fetch version requirements from various sources.
 *
 * Note: This is typically used internally by UpdateKit. Apps don't need to interact
 * with this interface directly.
 */
interface IAppUpdateRepository {

    /**
     * Check if an app update is available and determine the update type.
     *
     * This method will:
     * 1. Get the current app version code
     * 2. Fetch version thresholds from the configured source
     * 3. Compare versions to determine update type (IMMEDIATE, FLEXIBLE, or NO_UPDATE)
     *
     * @return [AppUpdateInfo] containing update type and whether to show update prompt
     */
    suspend fun checkForAppUpdate(): AppUpdateInfo
}
