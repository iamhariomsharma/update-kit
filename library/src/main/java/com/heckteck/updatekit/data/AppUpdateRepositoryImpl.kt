package com.heckteck.updatekit.data

import com.heckteck.updatekit.UpdateVersionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Implementation of app update repository that uses [UpdateVersionProvider] to fetch version thresholds.
 *
 * This implementation is source-agnostic - it doesn't care where version thresholds come from.
 * The provider can fetch from Firebase, API, local database, or any custom source.
 *
 * @property versionProvider Provider for app version and update thresholds
 */
internal class AppUpdateRepositoryImpl(
    private val versionProvider: UpdateVersionProvider
) : IAppUpdateRepository {

    override suspend fun checkForAppUpdate(): AppUpdateInfo = withContext(Dispatchers.IO) {
        try {
            val currentVersionCode = versionProvider.getCurrentVersionCode()
            val thresholds = versionProvider.getVersionThresholds()

            if (thresholds == null) {
                Timber.w("Unable to fetch version thresholds - provider returned null")
                return@withContext AppUpdateInfo(
                    updateType = UpdateType.NO_UPDATE,
                    shouldShowUpdate = false
                )
            }

            Timber.d("Version Check - Current: $currentVersionCode, ForceBelow: ${thresholds.forceUpdateBelowVersion}, RecommendedBelow: ${thresholds.recommendedUpdateBelowVersion}")

            val updateType = when {
                // IMMEDIATE: Current version is below the forced update threshold
                currentVersionCode < thresholds.forceUpdateBelowVersion -> {
                    Timber.d("IMMEDIATE update required: $currentVersionCode < ${thresholds.forceUpdateBelowVersion}")
                    UpdateType.IMMEDIATE
                }
                // FLEXIBLE: Current version is below the recommended update threshold
                currentVersionCode < thresholds.recommendedUpdateBelowVersion -> {
                    Timber.d("FLEXIBLE update recommended: $currentVersionCode < ${thresholds.recommendedUpdateBelowVersion}")
                    UpdateType.FLEXIBLE
                }
                // NO UPDATE: Current version is up to date
                else -> {
                    Timber.d("No update needed: $currentVersionCode >= ${thresholds.recommendedUpdateBelowVersion}")
                    UpdateType.NO_UPDATE
                }
            }

            AppUpdateInfo(
                updateType = updateType,
                shouldShowUpdate = updateType != UpdateType.NO_UPDATE
            )
        } catch (e: Exception) {
            Timber.e(e, "Error checking for app update")
            AppUpdateInfo(
                updateType = UpdateType.NO_UPDATE,
                shouldShowUpdate = false
            )
        }
    }
}

/**
 * Data class representing the result of an update check.
 *
 * @property updateType The type of update available
 * @property shouldShowUpdate Whether to show update prompt to user
 */
data class AppUpdateInfo(
    val updateType: UpdateType,
    val shouldShowUpdate: Boolean
)
