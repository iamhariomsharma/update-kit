package com.heckteck.updatekit.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heckteck.updatekit.data.InAppUpdateManager
import com.heckteck.updatekit.data.UpdateState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for app update dialogs and screens.
 */
data class AppUpdateUiState(
    val isLoading: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val isForceUpdate: Boolean = false,
    val updateState: UpdateState = UpdateState.Idle,
    val isMaintenanceMode: Boolean = false
)

/**
 * Events emitted by the update system for one-time actions.
 */
sealed class AppUpdateEvent {
    object UpdateCheckCompleted : AppUpdateEvent()
    data class ShowUpdateDialog(val isForced: Boolean) : AppUpdateEvent()
    data class UpdateError(val message: String) : AppUpdateEvent()
    object UpdateStarted : AppUpdateEvent()
    object UpdateDownloaded : AppUpdateEvent()
    object UpdateCompleted : AppUpdateEvent()
    object MaintenanceModeDetected : AppUpdateEvent()
}

/**
 * Actions that can be performed on the update system.
 */
sealed class AppUpdateAction {
    object CheckForUpdate : AppUpdateAction()
    object StartFlexibleUpdate : AppUpdateAction()
    object StartImmediateUpdate : AppUpdateAction()
    object DismissUpdateDialog : AppUpdateAction()
    object CompleteUpdate : AppUpdateAction()
    object RetryUpdate : AppUpdateAction()
    data class OpenPlayStore(val packageName: String) : AppUpdateAction()
}

/**
 * ViewModel for managing app update UI state and user interactions.
 *
 * This ViewModel observes the [InAppUpdateManager] state and translates it into
 * UI-friendly state and events. It handles user actions and coordinates with the manager.
 *
 * @property inAppUpdateManager The manager that handles update logic
 */
internal class AppUpdateViewModel(
    private val inAppUpdateManager: InAppUpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppUpdateEvent>()
    val events: SharedFlow<AppUpdateEvent> = _events.asSharedFlow()

    init {
        observeUpdateState()
    }

    /**
     * Handle user actions related to updates.
     */
    fun handleAction(action: AppUpdateAction) {
        viewModelScope.launch {
            when (action) {
                is AppUpdateAction.CheckForUpdate -> checkForUpdate()
                is AppUpdateAction.StartFlexibleUpdate -> startFlexibleUpdate()
                is AppUpdateAction.StartImmediateUpdate -> startImmediateUpdate()
                is AppUpdateAction.DismissUpdateDialog -> dismissUpdateDialog()
                is AppUpdateAction.CompleteUpdate -> completeUpdate()
                is AppUpdateAction.RetryUpdate -> retryUpdate()
                is AppUpdateAction.OpenPlayStore -> {
                    // Context will be provided from Composable
                }
            }
        }
    }

    /**
     * Open the Play Store for manual update.
     */
    fun openPlayStore(context: Context, packageName: String) {
        inAppUpdateManager.openPlayStoreForUpdate(context, packageName)
    }

    private fun observeUpdateState() {
        viewModelScope.launch {
            inAppUpdateManager.updateState.collect { state ->
                when (state) {
                    is UpdateState.Idle -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            updateState = state
                        )
                    }
                    is UpdateState.CheckingForUpdate -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            updateState = state
                        )
                    }
                    is UpdateState.NoUpdateAvailable -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showUpdateDialog = false,
                            updateState = state
                        )
                        _events.emit(AppUpdateEvent.UpdateCheckCompleted)
                    }
                    is UpdateState.UpdateAvailable -> {
                        val shouldForceUpdate = state.isImmediate

                        // Check if we should use custom dialog (when Play Store can't handle immediate update)
                        if (state.useCustomDialog) {
                            // Show custom non-dismissible dialog for force update
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                showUpdateDialog = true,
                                isForceUpdate = true,
                                updateState = state
                            )
                            _events.emit(AppUpdateEvent.ShowUpdateDialog(isForced = true))
                        } else {
                            // Use Google Play's update sheet
                            if (shouldForceUpdate) {
                                // Immediate update - directly start without dialog
                                startImmediateUpdate()
                            } else {
                                // Flexible update - directly start without dialog
                                startFlexibleUpdate()
                            }

                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                showUpdateDialog = false,
                                isForceUpdate = shouldForceUpdate,
                                updateState = state
                            )
                            _events.emit(AppUpdateEvent.UpdateStarted)
                        }
                    }
                    is UpdateState.MaintenanceMode -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isMaintenanceMode = true,
                            updateState = state
                        )
                        _events.emit(AppUpdateEvent.MaintenanceModeDetected)
                    }
                    is UpdateState.UpdateInProgress -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showUpdateDialog = false,
                            updateState = state
                        )
                        _events.emit(AppUpdateEvent.UpdateStarted)
                    }
                    is UpdateState.UpdateDownloaded -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            updateState = state
                        )
                        _events.emit(AppUpdateEvent.UpdateDownloaded)
                    }
                    is UpdateState.UpdateInstalling -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            updateState = state
                        )
                        // Update is being installed - app will restart soon
                    }
                    is UpdateState.UpdateCompleted -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            updateState = state
                        )
                        _events.emit(AppUpdateEvent.UpdateCompleted)
                    }
                    is UpdateState.UpdateFailed -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            updateState = state
                        )
                        _events.emit(AppUpdateEvent.UpdateError(state.error))
                    }
                }
            }
        }
    }

    private suspend fun checkForUpdate() {
        inAppUpdateManager.checkForUpdate()
    }

    private fun startFlexibleUpdate() {
        inAppUpdateManager.startFlexibleUpdateWithDefaultUI()
    }

    private fun startImmediateUpdate() {
        inAppUpdateManager.startImmediateUpdateWithDefaultUI()
    }

    private fun dismissUpdateDialog() {
        if (!_uiState.value.isForceUpdate) {
            _uiState.value = _uiState.value.copy(showUpdateDialog = false)
        }
    }

    private fun completeUpdate() {}

    private fun retryUpdate() {
        inAppUpdateManager.retryUpdate()
    }

    /**
     * Check for updates when the app starts or resumes.
     * Skips check if an update is already in progress.
     */
    fun checkForUpdateOnStart() {
        val currentState = _uiState.value.updateState
        if (currentState !is UpdateState.UpdateInProgress &&
            currentState !is UpdateState.UpdateDownloaded &&
            currentState !is UpdateState.UpdateInstalling &&
            currentState !is UpdateState.MaintenanceMode) {
            viewModelScope.launch {
                checkForUpdate()
            }
        }
    }
}
