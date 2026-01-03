package com.heckteck.updatekit.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

sealed class UpdateState {
    object Idle : UpdateState()
    object CheckingForUpdate : UpdateState()
    object NoUpdateAvailable : UpdateState()
    data class UpdateAvailable(
        val updateInfo: AppUpdateInfo,
        val isFlexible: Boolean,
        val isImmediate: Boolean,
        val useCustomDialog: Boolean = false
    ) : UpdateState()
    object UpdateInProgress : UpdateState()
    object UpdateDownloaded : UpdateState()
    object UpdateInstalling : UpdateState()
    data class UpdateFailed(val error: String) : UpdateState()
    object UpdateCompleted : UpdateState()
    object MaintenanceMode : UpdateState()
}

sealed class UpdateError(override val message: String) : Exception(message) {
    class UserCancelled : UpdateError("Update cancelled by user")
    class UpdateNotAvailable : UpdateError("No update available")
    class InstallFailed : UpdateError("Update installation failed")
    class UnknownError(error: Throwable) :
        UpdateError("Unknown error: ${error.message}")
}

/**
 * Core manager for handling in-app updates.
 *
 * This class orchestrates the Google Play In-App Update flow, managing:
 * - Update availability checks
 * - Flexible and immediate update flows
 * - Installation monitoring and state management
 * - Session tracking to avoid repeated prompts
 * - Error handling and retries
 *
 * Inject this into your Activity to control updates:
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private val updateManager: InAppUpdateManager by inject()
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         updateManager.initialize(this)
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
 * @property appUpdateRepository Repository for checking update requirements from configured source
 */
class InAppUpdateManager(
    private val appUpdateRepository: IAppUpdateRepository
) {

    private var activity: ComponentActivity? = null
    private lateinit var appUpdateManager: AppUpdateManager

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _updateError = MutableStateFlow<UpdateError?>(null)
    val updateError: StateFlow<UpdateError?> = _updateError.asStateFlow()

    private var currentUpdateInfo: AppUpdateInfo? = null
    private var lastDismissedUpdateTime: Long = 0L
    private val UPDATE_DISMISS_COOLDOWN = 30 * 60 * 1000L // 30 minutes
    private var immediateUpdatePending = false
    private var isShowingImmediateUpdate = false

    private var hasShownFlexibleUpdateThisSession = false
    private var sessionStartTime: Long = 0L

    private var updateLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var isInitialized = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val installStateListener = InstallStateUpdatedListener { state ->
        handleInstallState(state)
    }

    private var installationMonitorJob: Job? = null
    private var installationStartTime: Long = 0L
    private val INSTALLATION_TIMEOUT = 5 * 60 * 1000L // 5 minutes timeout

    fun initialize(activity: ComponentActivity) {
        if (isInitialized) {
            Timber.d("⚠️ InAppUpdateManager already initialized")
            return
        }

        Timber.d("🚀 Initializing InAppUpdateManager")
        this.activity = activity
        appUpdateManager = AppUpdateManagerFactory.create(activity)

        // Track session start time
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
            hasShownFlexibleUpdateThisSession = false
            Timber.d("📅 New session started at $sessionStartTime")
        }

        updateLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            Timber.d("📋 Update result received: resultCode=${result.resultCode}")
            handleUpdateResult(result.resultCode)
        }

        appUpdateManager.registerListener(installStateListener)
        isInitialized = true
        Timber.d("✅ InAppUpdateManager initialized successfully")

        // Check for any stalled updates
        checkForStalledUpdate()
    }

    fun cleanup() {
        if (isInitialized) {
            Timber.d("🧹 Cleaning up InAppUpdateManager")
            appUpdateManager.unregisterListener(installStateListener)
            installationMonitorJob?.cancel()
            installationMonitorJob = null
            activity = null
            updateLauncher = null
            isInitialized = false
            Timber.d("✅ InAppUpdateManager cleaned up")
        }
    }

    suspend fun checkForUpdate(): UpdateState {
        Timber.d("🔍 checkForUpdate called")
        Timber.d("   hasShownFlexibleUpdateThisSession: $hasShownFlexibleUpdateThisSession")
        Timber.d("   Session duration: ${(System.currentTimeMillis() - sessionStartTime) / 1000}s")

        return try {
            _updateState.value = UpdateState.CheckingForUpdate
            _updateError.value = null

            val backendUpdateInfo = appUpdateRepository.checkForAppUpdate()
            Timber.d("Backend update info: updateType=${backendUpdateInfo.updateType}, shouldShow=${backendUpdateInfo.shouldShowUpdate}")

            return when (backendUpdateInfo.updateType) {
                UpdateType.IMMEDIATE -> {
                    Timber.d("Triggering IMMEDIATE update flow")
                    immediateUpdatePending = true
                    checkPlayStoreUpdate(forceImmediate = true)
                }
                UpdateType.FLEXIBLE -> {
                    // Check if we've already shown flexible update this session
                    if (hasShownFlexibleUpdateThisSession) {
                        Timber.d("📝 Flexible update already shown this session, skipping backend-triggered check")
                        val state = UpdateState.NoUpdateAvailable
                        _updateState.value = state
                        return state
                    }

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastDismissedUpdateTime < UPDATE_DISMISS_COOLDOWN) {
                        Timber.d("Skipping flexible update - recently dismissed")
                        val state = UpdateState.NoUpdateAvailable
                        _updateState.value = state
                        return state
                    }
                    Timber.d("Triggering FLEXIBLE update flow")
                    checkPlayStoreUpdate(forceFlexible = true)
                }
                UpdateType.NO_UPDATE -> {
                    Timber.d("No update required from backend")
                    checkPlayStoreUpdate()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
            checkPlayStoreUpdate()
        }
    }

    private suspend fun checkPlayStoreUpdate(
        forceImmediate: Boolean = false,
        forceFlexible: Boolean = false
    ): UpdateState {
        return try {
            if (!isInitialized) {
                return UpdateState.NoUpdateAvailable
            }

            val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
            currentUpdateInfo = appUpdateInfo

            when {
                appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE -> {
                    val isFlexible: Boolean
                    val isImmediate: Boolean

                    when {
                        forceImmediate -> {
                            // When force immediate is requested, only allow immediate
                            isImmediate = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                            isFlexible = false
                            Timber.d("Force immediate update: immediate=$isImmediate")
                        }
                        forceFlexible -> {
                            // When force flexible is requested, only allow flexible
                            isFlexible = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                            isImmediate = false
                            Timber.d("Force flexible update: flexible=$isFlexible")
                        }
                        else -> {
                            // Default case - check what Play Store allows
                            isFlexible = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                            isImmediate = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                            Timber.d("Default update check: flexible=$isFlexible, immediate=$isImmediate")
                        }
                    }

                    if (!isFlexible && !isImmediate) {
                        // Update not allowed by Play Store
                        Timber.d("No update type allowed by Play Store")

                        // If backend requires immediate update but Play Store can't handle it, use custom dialog
                        if (forceImmediate) {
                            Timber.d("⚠️ Backend requires force update but Play Store can't handle it - using custom dialog")
                            val state = UpdateState.UpdateAvailable(
                                updateInfo = appUpdateInfo,
                                isFlexible = false,
                                isImmediate = true,
                                useCustomDialog = true
                            )
                            _updateState.value = state
                            return state
                        }

                        val state = UpdateState.NoUpdateAvailable
                        _updateState.value = state
                        return state
                    }

                    val state = UpdateState.UpdateAvailable(
                        updateInfo = appUpdateInfo,
                        isFlexible = isFlexible,
                        isImmediate = isImmediate,
                        useCustomDialog = false
                    )
                    _updateState.value = state
                    state
                }

                appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) || forceImmediate) {
                        activity?.let {
                            startImmediateUpdateWithDefaultUI(appUpdateInfo)
                        }
                    }
                    UpdateState.UpdateInProgress
                }

                else -> {
                    val state = UpdateState.NoUpdateAvailable
                    _updateState.value = state
                    state
                }
            }
        } catch (e: Exception) {
            val error = UpdateError.UnknownError(e)
            _updateError.value = error
            _updateState.value = UpdateState.UpdateFailed(error.message)
            UpdateState.UpdateFailed(error.message)
        }
    }

    fun startFlexibleUpdateWithDefaultUI() {
        // Check if we've already shown flexible update this session
        if (hasShownFlexibleUpdateThisSession) {
            Timber.d("📝 Flexible update already shown this session, skipping")
            return
        }

        val currentActivity = activity ?: run {
            handleUpdateError(UpdateError.UpdateNotAvailable())
            return
        }

        val launcher = updateLauncher ?: run {
            handleUpdateError(UpdateError.UpdateNotAvailable())
            return
        }

        currentUpdateInfo?.let { updateInfo ->
            if (updateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                currentActivity.lifecycleScope.launch {
                    try {
                        _updateState.value = UpdateState.UpdateInProgress

                        val updateOptions = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE)
                            .build()

                        // Mark that we've shown the flexible update
                        hasShownFlexibleUpdateThisSession = true
                        Timber.d("✅ Marked flexible update as shown for this session")

                        appUpdateManager.startUpdateFlowForResult(
                            updateInfo,
                            launcher,
                            updateOptions
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        handleUpdateError(UpdateError.InstallFailed())
                    } catch (e: Exception) {
                        handleUpdateError(UpdateError.UnknownError(e))
                    }
                }
            } else {
                handleUpdateError(UpdateError.UpdateNotAvailable())
            }
        } ?: handleUpdateError(UpdateError.UpdateNotAvailable())
    }

    fun startImmediateUpdateWithDefaultUI(updateInfo: AppUpdateInfo? = currentUpdateInfo) {
        if (isShowingImmediateUpdate) {
            Timber.d("Already showing immediate update dialog, skipping")
            return
        }

        val currentActivity = activity ?: run {
            handleUpdateError(UpdateError.UpdateNotAvailable())
            return
        }

        val launcher = updateLauncher ?: run {
            handleUpdateError(UpdateError.UpdateNotAvailable())
            return
        }

        val info = updateInfo ?: run {
            handleUpdateError(UpdateError.UpdateNotAvailable())
            return
        }

        if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            currentActivity.lifecycleScope.launch {
                try {
                    isShowingImmediateUpdate = true
                    _updateState.value = UpdateState.UpdateInProgress

                    val updateOptions = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE)
                        .build()

                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        launcher,
                        updateOptions
                    )
                } catch (e: IntentSender.SendIntentException) {
                    isShowingImmediateUpdate = false
                    handleUpdateError(UpdateError.InstallFailed())
                } catch (e: Exception) {
                    isShowingImmediateUpdate = false
                    handleUpdateError(UpdateError.UnknownError(e))
                }
            }
        } else {
            handleUpdateError(UpdateError.UpdateNotAvailable())
        }
    }

    private fun handleUpdateResult(resultCode: Int) {
        isShowingImmediateUpdate = false

        when (resultCode) {
            Activity.RESULT_OK -> {
                _updateState.value = UpdateState.UpdateInProgress
                immediateUpdatePending = false
            }

            Activity.RESULT_CANCELED -> {
                val state = _updateState.value

                // Check if this is an immediate update that needs to be re-triggered
                if (immediateUpdatePending || (state is UpdateState.UpdateAvailable && state.isImmediate)) {
                    // For immediate updates, we need to re-trigger the update with a proper delay
                    Timber.d("User dismissed immediate update, will re-trigger after delay")
                    immediateUpdatePending = true

                    // Re-trigger the update flow with proper delay to avoid blinking
                    coroutineScope.launch {
                        delay(800)
                        if (immediateUpdatePending && currentUpdateInfo != null) {
                            try {
                                val updateInfo = appUpdateManager.appUpdateInfo.await()
                                if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                                    updateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                                    startImmediateUpdateWithDefaultUI(updateInfo)
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to re-trigger immediate update")
                                currentUpdateInfo?.let {
                                    startImmediateUpdateWithDefaultUI(it)
                                }
                            }
                        }
                    }
                } else if (state is UpdateState.UpdateAvailable && state.isFlexible) {
                    lastDismissedUpdateTime = System.currentTimeMillis()
                    hasShownFlexibleUpdateThisSession = true
                    Timber.d("User dismissed flexible update, marking as shown for this session")
                    handleUpdateError(UpdateError.UserCancelled())
                } else {
                    handleUpdateError(UpdateError.UserCancelled())
                }
            }

            else -> {
                handleUpdateError(UpdateError.InstallFailed())
            }
        }
    }

    private fun completeFlexibleUpdate() {
        Timber.d("📱 Completing flexible update - app will restart")
        coroutineScope.launch {
            try {
                // Check the current status before completing
                val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
                val status = appUpdateInfo.installStatus()

                Timber.d("Current status before completing: ${getInstallStatusName(status)}")

                if (status == InstallStatus.DOWNLOADED || status == InstallStatus.INSTALLED) {
                    Timber.d("✅ Calling completeUpdate() to restart app")
                    // For flexible updates, completeUpdate() will restart the app
                    appUpdateManager.completeUpdate()
                    _updateState.value = UpdateState.UpdateCompleted
                    Timber.d("⚠️ If you see this log, the app restart might have failed")
                } else {
                    Timber.w("Cannot complete update - wrong status: ${getInstallStatusName(status)}")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to complete flexible update")
                handleUpdateError(UpdateError.InstallFailed())
            }
        }
    }

    fun manuallyCompleteFlexibleUpdate() {
        // Public method to allow manual triggering of update completion
        if (_updateState.value == UpdateState.UpdateDownloaded) {
            completeFlexibleUpdate()
        }
    }

    private fun checkForStalledUpdate() {
        activity?.lifecycleScope?.launch {
            try {
                val appUpdateInfo = appUpdateManager.appUpdateInfo.await()

                when (appUpdateInfo.installStatus()) {
                    InstallStatus.DOWNLOADED -> {
                        Timber.d("✅ Found downloaded update ready to install")
                        _updateState.value = UpdateState.UpdateDownloaded
                        showUpdateReadyNotification()
                    }
                    InstallStatus.INSTALLING -> {
                        Timber.d("⚙️ Update is currently installing")
                        if (_updateState.value !is UpdateState.UpdateInstalling) {
                            _updateState.value = UpdateState.UpdateInstalling
                        }
                        // Monitor installation progress with timeout
                        monitorInstallationProgress(appUpdateInfo)
                        immediateUpdatePending = false
                    }
                    InstallStatus.INSTALLED -> {
                        Timber.d("✅ Update has been installed successfully")
                        _updateState.value = UpdateState.UpdateCompleted
                        immediateUpdatePending = false
                        handleInstalledUpdate()
                    }
                    InstallStatus.PENDING -> {
                        Timber.d("⏳ Update is pending")
                        _updateState.value = UpdateState.UpdateInProgress
                    }
                    else -> {
                        // Check if there's an update in progress that was interrupted
                        if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                            Timber.d("Found update in progress, resuming...")
                            // Check from backend first to determine update type
                            coroutineScope.launch {
                                try {
                                    val backendUpdateInfo = appUpdateRepository.checkForAppUpdate()
                                    if (backendUpdateInfo.updateType == UpdateType.IMMEDIATE) {
                                        immediateUpdatePending = true
                                        startImmediateUpdateWithDefaultUI(appUpdateInfo)
                                    } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                                        // Resume flexible update
                                        _updateState.value = UpdateState.UpdateInProgress
                                    }
                                } catch (e: Exception) {
                                    // Fallback to checking Play Store allowed types
                                    if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                                        immediateUpdatePending = true
                                        startImmediateUpdateWithDefaultUI(appUpdateInfo)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Stalled update check failed")
            }
        }
    }

    private fun handleInstallState(state: InstallState) {
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytes = state.totalBytesToDownload()
                val progress = if (totalBytes > 0) {
                    (bytesDownloaded * 100 / totalBytes)
                } else 0
                Timber.d("📥 Downloading: $bytesDownloaded / $totalBytes bytes ($progress%)")

                if (_updateState.value !is UpdateState.UpdateInProgress) {
                    _updateState.value = UpdateState.UpdateInProgress
                }
            }

            InstallStatus.DOWNLOADED -> {
                Timber.d("✅ Update downloaded, ready to install")
                // Only update state if not already downloaded
                if (_updateState.value !is UpdateState.UpdateDownloaded) {
                    _updateState.value = UpdateState.UpdateDownloaded
                    showUpdateReadyNotification()
                }
            }

            InstallStatus.INSTALLING -> {
                Timber.d("⚙️ Update is installing")

                if (installationStartTime == 0L) {
                    installationStartTime = System.currentTimeMillis()
                }

                if (_updateState.value !is UpdateState.UpdateInstalling) {
                    _updateState.value = UpdateState.UpdateInstalling
                    coroutineScope.launch {
                        try {
                            val updateInfo = appUpdateManager.appUpdateInfo.await()
                            monitorInstallationProgress(updateInfo)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to start installation monitor")
                        }
                    }
                }

                // For immediate updates, the app will restart automatically
                // For flexible updates, we need to wait for INSTALLED status
                currentUpdateInfo?.let { info ->
                    if (info.updatePriority() >= 4 || immediateUpdatePending) {
                        // High priority or immediate update - app will restart
                        Timber.d("🚀 Immediate update installing - app will restart automatically")
                        immediateUpdatePending = false
                    } else {
                        Timber.d("📦 Flexible update installing - waiting for completion")
                    }
                }
            }

            InstallStatus.INSTALLED -> {
                Timber.d("🎉 Update installed successfully!")
                _updateState.value = UpdateState.UpdateCompleted
                immediateUpdatePending = false
                installationStartTime = 0L

                // Handle the installed update
                handleInstalledUpdate()
            }

            InstallStatus.FAILED -> {
                val errorCode = state.installErrorCode()
                Timber.e("❌ Update installation failed with error code: $errorCode")
                Timber.e("Error details: ${getInstallErrorMessage(errorCode)}")
                handleUpdateError(UpdateError.InstallFailed())
                immediateUpdatePending = false
                installationStartTime = 0L
            }

            InstallStatus.CANCELED -> {
                Timber.d("⚠️ Update cancelled by user")
                handleUpdateError(UpdateError.UserCancelled())
                installationStartTime = 0L
            }

            InstallStatus.PENDING -> {
                Timber.d("⏳ Update pending")
                _updateState.value = UpdateState.UpdateInProgress
            }

            InstallStatus.UNKNOWN -> {
                Timber.w("❓ Unknown install status")
            }

            else -> {
                Timber.w("⚠️ Unhandled install status: ${state.installStatus()}")
            }
        }
    }

    private fun getInstallErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            0 -> "NO_ERROR"
            -1 -> "NO_ERROR_PARTIALLY_ALLOWED"
            -2 -> "ERROR_UNKNOWN"
            -3 -> "ERROR_API_NOT_AVAILABLE"
            -4 -> "ERROR_INVALID_REQUEST"
            -5 -> "ERROR_INSTALL_UNAVAILABLE"
            -6 -> "ERROR_INSTALL_NOT_ALLOWED"
            -7 -> "ERROR_DOWNLOAD_NOT_PRESENT"
            -8 -> "ERROR_INSTALL_IN_PROGRESS"
            -9 -> "ERROR_INTERNAL_ERROR"
            -10 -> "ERROR_PLAY_STORE_NOT_FOUND"
            -100 -> "ERROR_APP_NOT_OWNED"
            else -> "UNKNOWN_ERROR_CODE_$errorCode"
        }
    }

    private fun showUpdateReadyNotification() {
        // This is called when a flexible update has been downloaded
        // we can show a snackbar or notification to the user
        Timber.d("Flexible update ready to install - user should restart the app")
        // For now, we'll complete it automatically after a delay
        coroutineScope.launch {
            delay(3000) // Give user 3 seconds to see any UI indication
            completeFlexibleUpdate()
        }
    }

    private fun handleUpdateError(error: UpdateError) {
        if (error !is UpdateError.UserCancelled || !immediateUpdatePending) {
            _updateError.value = error
            _updateState.value = UpdateState.UpdateFailed(error.message)
        }
    }

    fun clearError() {
        _updateError.value = null
        _updateState.value = UpdateState.Idle
    }

    fun retryUpdate() {
        val state = _updateState.value
        if (state is UpdateState.UpdateFailed) {
            clearError()
            coroutineScope.launch {
                checkForUpdate()
            }
        }
    }

    fun checkAndRetriggerImmediateUpdateIfNeeded() {
        // Also check for stuck installation state
        val currentState = _updateState.value
        if (currentState is UpdateState.UpdateInstalling) {
            val elapsedTime = if (installationStartTime > 0) {
                System.currentTimeMillis() - installationStartTime
            } else 0L

            if (elapsedTime > 30000) { // If stuck for more than 30 seconds
                Timber.w("⚠️ Installation seems stuck for ${elapsedTime / 1000}s, checking actual status")
                checkForStalledUpdate()
            }
        }

        // When app resumes, check if we have a pending immediate update
        if (immediateUpdatePending) {
            Timber.d("📱 App resumed with pending immediate update")
            coroutineScope.launch {
                delay(500) // Small delay to ensure UI is ready

                try {
                    // Re-check update status from Play Store
                    val appUpdateInfo = appUpdateManager.appUpdateInfo.await()

                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                        appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {

                        currentUpdateInfo = appUpdateInfo

                        // Check if immediate update is still required from backend
                        try {
                            val backendUpdateInfo = appUpdateRepository.checkForAppUpdate()
                            if (backendUpdateInfo.updateType == UpdateType.IMMEDIATE) {
                                Timber.d("✅ Backend still requires immediate update, re-triggering")
                                startImmediateUpdateWithDefaultUI(appUpdateInfo)
                            } else {
                                // Backend no longer requires immediate update
                                Timber.d("ℹ️ Backend no longer requires immediate update")
                                immediateUpdatePending = false
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Backend check failed, falling back to Play Store")
                            // If backend check fails, continue with immediate update
                            if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                                startImmediateUpdateWithDefaultUI(appUpdateInfo)
                            }
                        }
                    } else {
                        Timber.d("ℹ️ Update is no longer available")
                        immediateUpdatePending = false
                        _updateState.value = UpdateState.NoUpdateAvailable
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to check update status on resume")
                    currentUpdateInfo?.let {
                        Timber.d("⚠️ Using stored update info as fallback")
                        startImmediateUpdateWithDefaultUI(it)
                    }
                }
            }
        } else {
            Timber.d("ℹ️ No pending immediate update")
        }
    }

    fun startPeriodicUpdateCheck(intervalMillis: Long = 24 * 60 * 60 * 1000) {
        coroutineScope.launch {
            while (true) {
                delay(intervalMillis)
                if (isInitialized) {
                    checkForUpdate()
                }
            }
        }
    }

    fun resetSessionTracking() {
        hasShownFlexibleUpdateThisSession = false
        sessionStartTime = System.currentTimeMillis()
        Timber.d("🔄 Session tracking reset")
    }

    fun isFlexibleUpdateShownThisSession(): Boolean {
        return hasShownFlexibleUpdateThisSession
    }

    fun openPlayStoreForUpdate(context: Context, packageName: String) {
        try {
            Timber.d("📱 Opening Play Store for package: $packageName")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open Play Store app, trying browser fallback")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to open Play Store in browser")
            }
        }
    }

    private fun getInstallStatusName(status: Int): String {
        return when (status) {
            InstallStatus.PENDING -> "PENDING"
            InstallStatus.DOWNLOADING -> "DOWNLOADING"
            InstallStatus.DOWNLOADED -> "DOWNLOADED"
            InstallStatus.INSTALLING -> "INSTALLING"
            InstallStatus.INSTALLED -> "INSTALLED"
            InstallStatus.FAILED -> "FAILED"
            InstallStatus.CANCELED -> "CANCELED"
            InstallStatus.UNKNOWN -> "UNKNOWN"
            else -> "UNKNOWN_STATUS_$status"
        }
    }

    private fun monitorInstallationProgress(appUpdateInfo: AppUpdateInfo) {
        Timber.d("🔍 Starting installation progress monitor")

        // Cancel any existing monitor
        installationMonitorJob?.cancel()
        installationStartTime = System.currentTimeMillis()

        installationMonitorJob = coroutineScope.launch {
            var checkCount = 0
            val maxChecks = 60 // 60 checks * 5 seconds = 5 minutes max

            while (checkCount < maxChecks) {
                delay(5000) // Check every 5 seconds
                checkCount++

                try {
                    val currentInfo = appUpdateManager.appUpdateInfo.await()
                    val currentStatus = currentInfo.installStatus()
                    val elapsedTime = System.currentTimeMillis() - installationStartTime

                    Timber.d("📊 Installation Monitor Check #$checkCount")
                    Timber.d("   Status: ${getInstallStatusName(currentStatus)}")
                    Timber.d("   Elapsed Time: ${elapsedTime / 1000}s")

                    when (currentStatus) {
                        InstallStatus.INSTALLED -> {
                            Timber.d("✅ Installation completed successfully!")
                            _updateState.value = UpdateState.UpdateCompleted
                            immediateUpdatePending = false
                            handleInstalledUpdate()
                            break
                        }
                        InstallStatus.FAILED -> {
                            Timber.e("❌ Installation failed!")
                            handleUpdateError(UpdateError.InstallFailed())
                            break
                        }
                        InstallStatus.CANCELED -> {
                            Timber.d("⚠️ Installation was cancelled")
                            handleUpdateError(UpdateError.UserCancelled())
                            break
                        }
                        InstallStatus.DOWNLOADING -> {
                            Timber.d("📥 Still downloading...")
                            _updateState.value = UpdateState.UpdateInProgress
                        }
                        InstallStatus.DOWNLOADED -> {
                            Timber.d("📦 Downloaded, waiting for installation...")
                            _updateState.value = UpdateState.UpdateDownloaded
                        }
                        InstallStatus.INSTALLING -> {
                            Timber.d("⚙️ Still installing...")
                            // Check for timeout
                            if (elapsedTime > INSTALLATION_TIMEOUT) {
                                Timber.e("⏱️ Installation timeout exceeded!")
                                _updateState.value = UpdateState.UpdateFailed("Installation timeout")
                                break
                            }
                        }
                        InstallStatus.PENDING -> {
                            Timber.d("⏳ Installation pending...")
                        }
                        InstallStatus.UNKNOWN -> {
                            Timber.d("❓ Unknown installation status")
                        }
                        else -> {
                            Timber.d("⚠️ Unhandled status during monitoring: ${getInstallStatusName(currentStatus)}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error checking installation status")
                }
            }

            if (checkCount >= maxChecks) {
                Timber.e("❌ Installation monitor reached max checks limit")
                _updateState.value = UpdateState.UpdateFailed("Installation monitor timeout")
            }
        }
    }

    private fun handleInstalledUpdate() {
        Timber.d("🎉 Handling installed update")

        // Cancel installation monitor
        installationMonitorJob?.cancel()
        installationMonitorJob = null

        // For flexible updates, we need to complete the update to restart
        val state = _updateState.value
        if (state is UpdateState.UpdateDownloaded || state is UpdateState.UpdateCompleted) {
            Timber.d("📱 Preparing to restart app for flexible update")
            // The app should restart when completeUpdate is called
            // But sometimes it doesn't, so we can force it
            coroutineScope.launch {
                delay(1000) // Small delay to ensure UI is updated
                try {
                    appUpdateManager.completeUpdate()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to complete update, app might need manual restart")
                }
            }
        }
    }
}
