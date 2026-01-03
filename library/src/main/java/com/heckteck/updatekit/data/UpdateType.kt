package com.heckteck.updatekit.data

/**
 * Enum representing the type of update required for the app.
 */
enum class UpdateType(val value: String) {
    /**
     * Flexible update - User can continue using the app while update downloads in background.
     * A prompt will be shown suggesting the update, but it's not mandatory.
     */
    FLEXIBLE("FLEXIBLE"),

    /**
     * Immediate update - User must update the app before continuing to use it.
     * This is typically used for critical updates (security, breaking changes, etc.).
     */
    IMMEDIATE("IMMEDIATE"),

    /**
     * No update needed - The app is up to date.
     */
    NO_UPDATE("NO_UPDATE");

    companion object {
        /**
         * Convert string value to UpdateType enum.
         * @param value String representation of update type
         * @return Corresponding UpdateType, or NO_UPDATE if not found
         */
        fun fromString(value: String): UpdateType {
            return entries.find { it.value == value } ?: NO_UPDATE
        }
    }
}
