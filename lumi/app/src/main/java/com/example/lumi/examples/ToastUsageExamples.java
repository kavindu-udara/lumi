package com.example.lumi.examples;

import android.app.Activity;
import com.example.lumi.lib.Toast;
import com.example.lumi.lib.ToastManager;

/**
 * Example usage class demonstrating how to use the modern Toast notification system.
 * Copy and paste these patterns into your activities.
 */
public class ToastUsageExamples {

    /**
     * Example 1: Basic usage with Activity context (Recommended)
     */
    public static void example1_BasicUsage(Activity activity) {
        // Success toast - displays for 2 seconds
        Toast.success(activity, "Operation successful!");

        // Warning toast - displays for 2 seconds
        Toast.warning(activity, "Please check this field");

        // Error toast - displays for 4 seconds
        Toast.error(activity, "Something went wrong");
    }

    /**
     * Example 2: Using fallback system toast (no Activity required)
     */
    public static void example2_FallbackToast() {
        Toast.success("Quick success");
        Toast.warning("Quick warning");
        Toast.error("Quick error");
    }

    /**
     * Example 3: Using ToastManager for cleaner code
     */
    public static void example3_UsingToastManager(Activity activity) {
        ToastManager.showSuccess(activity, "User created successfully");
        ToastManager.showWarning(activity, "Email not verified");
        ToastManager.showError(activity, "Failed to save changes");
    }

    /**
     * Example 4: Showing toasts with delay
     */
    public static void example4_DelayedToasts(Activity activity) {
        // Show warning after 1 second
        ToastManager.showWarningDelayed(activity, "Processing...", 1000);

        // Show success after 3 seconds
        ToastManager.showSuccessDelayed(activity, "Operation complete", 3000);

        // Show error after 2 seconds
        ToastManager.showErrorDelayed(activity, "Timeout occurred", 2000);
    }

    /**
     * Example 5: Using in Button Click Listeners
     */
    public static void example5_ButtonClickHandlers(Activity activity) {
        // Success - login successful
        activity.findViewById(android.R.id.content).setOnClickListener(v ->
            Toast.success(activity, "Logged in successfully!")
        );

        // Warning - weak password
        activity.findViewById(android.R.id.content).setOnClickListener(v ->
            Toast.warning(activity, "Password is weak, use uppercase letters")
        );

        // Error - network error
        activity.findViewById(android.R.id.content).setOnClickListener(v ->
            Toast.error(activity, "Network connection failed")
        );
    }

    /**
     * Example 6: Use in API callbacks
     */
    public static void example6_APICallback(Activity activity) {
        // Simulated API callback
        simulateAPICall(activity);
    }

    private static void simulateAPICall(Activity activity) {
        // On success
        boolean success = true;
        if (success) {
            Toast.success(activity, "Data loaded successfully");
        } else {
            // On error
            Toast.error(activity, "Failed to load data");
        }
    }

    /**
     * Example 7: Conditional toasts based on user actions
     */
    public static void example7_ConditionalToasts(Activity activity, String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            Toast.warning(activity, "Please enter a value");
        } else if (userInput.length() < 3) {
            Toast.warning(activity, "Input must be at least 3 characters");
        } else if (!isValidEmail(userInput)) {
            Toast.error(activity, "Invalid email format");
        } else {
            Toast.success(activity, "Input is valid!");
        }
    }

    /**
     * Example 8: Form validation with toasts
     */
    public static boolean example8_FormValidation(Activity activity, String email, String password) {
        // Validate email
        if (email == null || email.isEmpty()) {
            Toast.warning(activity, "Email is required");
            return false;
        }

        // Validate email format
        if (!isValidEmail(email)) {
            Toast.error(activity, "Invalid email format");
            return false;
        }

        // Validate password
        if (password == null || password.isEmpty()) {
            Toast.warning(activity, "Password is required");
            return false;
        }

        // Validate password length
        if (password.length() < 6) {
            Toast.warning(activity, "Password must be at least 6 characters");
            return false;
        }

        // All validations passed
        Toast.success(activity, "Form is valid");
        return true;
    }

    /**
     * Example 9: Using with try-catch blocks
     */
    public static void example9_ErrorHandling(Activity activity) {
        try {
            // Some operation that might fail
            String result = performRiskyOperation();
            Toast.success(activity, "Operation completed: " + result);
        } catch (Exception e) {
            Toast.error(activity, "Error: " + e.getMessage());
        }
    }

    /**
     * Example 10: Batch operations with progress toasts
     */
    public static void example10_BatchOperations(Activity activity) {
        Toast.warning(activity, "Processing 5 items...");

        // After processing
        boolean allSuccessful = true;

        if (allSuccessful) {
            Toast.success(activity, "All 5 items processed successfully");
        } else {
            Toast.error(activity, "Some items failed to process");
        }
    }

    // Helper method for email validation
    private static boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    // Helper method for simulating risky operation
    private static String performRiskyOperation() throws Exception {
        return "Success";
    }
}

