package com.example.lumi.lib;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

/**
 * A modern Toast implementation that displays beautiful custom popups
 * for success, warning, and error messages.
 *
 * Usage Examples:
 * - Toast.success(activity, "Operation successful!");
 * - Toast.warning(activity, "Warning message");
 * - Toast.error(activity, "Error occurred");
 * - Toast.success("Quick success message"); // Uses fallback system toast
 */
public class ToastManager {

    /**
     * Shows a success toast message
     * @param activity The activity context (optional, can be null for system toast)
     * @param message The message to display
     */
    public static void showSuccess(Activity activity, String message) {
        Toast.success(activity, message);
    }

    /**
     * Shows a success toast message without requiring activity
     * @param message The message to display
     */
    public static void showSuccess(String message) {
        Toast.success(message);
    }

    /**
     * Shows a warning toast message
     * @param activity The activity context (optional, can be null for system toast)
     * @param message The message to display
     */
    public static void showWarning(Activity activity, String message) {
        Toast.warning(activity, message);
    }

    /**
     * Shows a warning toast message without requiring activity
     * @param message The message to display
     */
    public static void showWarning(String message) {
        Toast.warning(message);
    }

    /**
     * Shows an error toast message
     * @param activity The activity context (optional, can be null for system toast)
     * @param message The message to display
     */
    public static void showError(Activity activity, String message) {
        Toast.error(activity, message);
    }

    /**
     * Shows an error toast message without requiring activity
     * @param message The message to display
     */
    public static void showError(String message) {
        Toast.error(message);
    }

    /**
     * Shows a success toast after a delay
     * @param activity The activity context
     * @param message The message to display
     * @param delayMs Delay in milliseconds before showing the toast
     */
    public static void showSuccessDelayed(Activity activity, String message, long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(
            () -> Toast.success(activity, message),
            delayMs
        );
    }

    /**
     * Shows a warning toast after a delay
     * @param activity The activity context
     * @param message The message to display
     * @param delayMs Delay in milliseconds before showing the toast
     */
    public static void showWarningDelayed(Activity activity, String message, long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(
            () -> Toast.warning(activity, message),
            delayMs
        );
    }

    /**
     * Shows an error toast after a delay
     * @param activity The activity context
     * @param message The message to display
     * @param delayMs Delay in milliseconds before showing the toast
     */
    public static void showErrorDelayed(Activity activity, String message, long delayMs) {
        new Handler(Looper.getMainLooper()).postDelayed(
            () -> Toast.error(activity, message),
            delayMs
        );
    }
}

