package com.example.lumi.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.annotation.NonNull;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.lumi.R;
import com.example.lumi.activities.SignIn;
import com.example.lumi.lib.API;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.lib.Toast;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Locale;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "lumi_settings";
    private static final String KEY_DARK_THEME = "dark_theme";

    AppCompatActivity parent;
    private SessionManager sessionManager;

    public SettingsFragment() {
        // Required empty public constructor for Fragment recreation.
    }

    public SettingsFragment(AppCompatActivity parent) {
        this.parent = parent;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof AppCompatActivity) {
            parent = (AppCompatActivity) context;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        sessionManager = new SessionManager(requireContext());
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean(KEY_DARK_THEME, false);
        int onSurfaceColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK);
        int surfaceColor = resolveThemeColor(com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE);
        int backgroundColor = resolveThemeColor(android.R.attr.colorBackground, android.graphics.Color.WHITE);

        LinearLayout root = new LinearLayout(requireContext());
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(backgroundColor);

        TextView title = new TextView(requireContext());
        title.setText("Settings");
        title.setTextSize(22f);
        title.setTextColor(onSurfaceColor);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        MaterialCardView changePlanNav = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardLp.topMargin = dp(16);
        changePlanNav.setLayoutParams(cardLp);
        changePlanNav.setRadius(dp(12));
        changePlanNav.setStrokeWidth(dp(1));
        changePlanNav.setStrokeColor(getResources().getColor(R.color.gray_300, null));
        changePlanNav.setCardBackgroundColor(surfaceColor);
        changePlanNav.setClickable(true);
        changePlanNav.setFocusable(true);

        TextView rowLabel = new TextView(requireContext());
        rowLabel.setText("Change Plan");
        rowLabel.setTextSize(16f);
        rowLabel.setTextColor(onSurfaceColor);
        int rowPadH = dp(16);
        int rowPadV = dp(14);
        rowLabel.setPadding(rowPadH, rowPadV, rowPadH, rowPadV);
        changePlanNav.addView(rowLabel);
        root.addView(changePlanNav);

        changePlanNav.setOnClickListener(v -> parent.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new com.example.lumi.fragments.ChangePlanFragment())
                .addToBackStack("change_plan")
                .commit());

        MaterialCardView logoutNav = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams logoutCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        logoutCardLp.topMargin = dp(12);
        logoutNav.setLayoutParams(logoutCardLp);
        logoutNav.setRadius(dp(12));
        logoutNav.setStrokeWidth(dp(1));
        logoutNav.setStrokeColor(getResources().getColor(R.color.gray_300, null));
        logoutNav.setCardBackgroundColor(surfaceColor);
        logoutNav.setClickable(true);
        logoutNav.setFocusable(true);

        TextView logoutLabel = new TextView(requireContext());
        logoutLabel.setText("Logout");
        logoutLabel.setTextSize(16f);
        logoutLabel.setTextColor(onSurfaceColor);
        logoutLabel.setPadding(rowPadH, rowPadV, rowPadH, rowPadV);
        logoutNav.addView(logoutLabel);
        root.addView(logoutNav);

        logoutNav.setOnClickListener(v -> performLogout());

        MaterialCardView themeCard = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams themeCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        themeCardLp.topMargin = dp(12);
        themeCard.setLayoutParams(themeCardLp);
        themeCard.setRadius(dp(12));
        themeCard.setStrokeWidth(dp(1));
        themeCard.setStrokeColor(getResources().getColor(R.color.gray_300, null));
        themeCard.setCardBackgroundColor(surfaceColor);

        LinearLayout themeRow = new LinearLayout(requireContext());
        themeRow.setOrientation(LinearLayout.HORIZONTAL);
        themeRow.setPadding(rowPadH, rowPadV, rowPadH, rowPadV);
        themeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView themeLabel = new TextView(requireContext());
        themeLabel.setText("Dark Theme");
        themeLabel.setTextSize(16f);
        themeLabel.setTextColor(onSurfaceColor);
        LinearLayout.LayoutParams themeLabelLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        themeLabel.setLayoutParams(themeLabelLp);

        SwitchCompat themeSwitch = new SwitchCompat(requireContext());
        themeSwitch.setChecked(isDarkTheme);
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> applyTheme(isChecked));

        themeRow.addView(themeLabel);
        themeRow.addView(themeSwitch);
        themeCard.addView(themeRow);
        root.addView(themeCard);

        MaterialCardView usageCard = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams usageCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        usageCardLp.topMargin = dp(16);
        usageCard.setLayoutParams(usageCardLp);
        usageCard.setRadius(dp(12));
        usageCard.setStrokeWidth(dp(1));
        usageCard.setStrokeColor(getResources().getColor(R.color.gray_300, null));
        usageCard.setCardBackgroundColor(surfaceColor);

        LinearLayout usageBody = new LinearLayout(requireContext());
        usageBody.setOrientation(LinearLayout.VERTICAL);
        int usagePad = dp(16);
        usageBody.setPadding(usagePad, usagePad, usagePad, usagePad);

        TextView usageTitle = new TextView(requireContext());
        usageTitle.setText("Storage Usage");
        usageTitle.setTextSize(17f);
        usageTitle.setTypeface(usageTitle.getTypeface(), android.graphics.Typeface.BOLD);
        usageTitle.setTextColor(onSurfaceColor);

        TextView usagePlanText = new TextView(requireContext());
        usagePlanText.setText("Plan: -");
        usagePlanText.setTextColor(getResources().getColor(R.color.gray_600, null));
        LinearLayout.LayoutParams usagePlanLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        usagePlanLp.topMargin = dp(8);
        usagePlanText.setLayoutParams(usagePlanLp);

        ProgressBar usageProgress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        usageProgress.setMax(100);
        usageProgress.setIndeterminate(true);
        LinearLayout.LayoutParams usageProgressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        usageProgressLp.topMargin = dp(10);
        usageProgress.setLayoutParams(usageProgressLp);

        TextView usageAmountText = new TextView(requireContext());
        usageAmountText.setText("Used: loading...");
        usageAmountText.setTextColor(getResources().getColor(R.color.gray_600, null));
        LinearLayout.LayoutParams usageAmountLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        usageAmountLp.topMargin = dp(8);
        usageAmountText.setLayoutParams(usageAmountLp);

        TextView usageStatusText = new TextView(requireContext());
        usageStatusText.setText("Loading usage...");
        usageStatusText.setTextColor(getResources().getColor(R.color.gray_600, null));
        LinearLayout.LayoutParams usageStatusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        usageStatusLp.topMargin = dp(4);
        usageStatusText.setLayoutParams(usageStatusLp);

        usageBody.addView(usageTitle);
        usageBody.addView(usagePlanText);
        usageBody.addView(usageProgress);
        usageBody.addView(usageAmountText);
        usageBody.addView(usageStatusText);
        usageCard.addView(usageBody);
        root.addView(usageCard);

        TextView profileTitle = new TextView(requireContext());
        LinearLayout.LayoutParams profileTitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        profileTitleLp.topMargin = dp(24);
        profileTitle.setLayoutParams(profileTitleLp);
        profileTitle.setText("Update Profile");
        profileTitle.setTextSize(18f);
        profileTitle.setTextColor(onSurfaceColor);
        profileTitle.setTypeface(profileTitle.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(profileTitle);

        TextView emailText = new TextView(requireContext());
        emailText.setText("Email: -");
        emailText.setTextColor(getResources().getColor(R.color.gray_600, null));
        LinearLayout.LayoutParams emailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        emailLp.topMargin = dp(8);
        emailText.setLayoutParams(emailLp);
        root.addView(emailText);

        EditText nameInput = new EditText(requireContext());
        nameInput.setHint("Display name");
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nameLp.topMargin = dp(12);
        nameInput.setLayoutParams(nameLp);
        root.addView(nameInput);

        Button updateProfileButton = new Button(requireContext());
        updateProfileButton.setText("Update Profile");
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonLp.topMargin = dp(12);
        updateProfileButton.setLayoutParams(buttonLp);
        root.addView(updateProfileButton);

        String supabaseToken = sessionManager.getToken();
        JsonObject sessionUser = sessionManager.getUser();
        String email = sessionUser != null && sessionUser.has("email")
                ? sessionUser.get("email").getAsString() : "-";
        emailText.setText("Email: " + email);
        if (sessionUser != null && sessionUser.has("user_metadata")
                && sessionUser.get("user_metadata").isJsonObject()) {
            JsonObject metadata = sessionUser.getAsJsonObject("user_metadata");
            if (metadata.has("displayName") && !metadata.get("displayName").isJsonNull()) {
                nameInput.setText(metadata.get("displayName").getAsString());
            }
        }

        if (supabaseToken == null || supabaseToken.trim().isEmpty()) {
            updateProfileButton.setEnabled(false);
            nameInput.setEnabled(false);
            usageProgress.setIndeterminate(false);
            usageProgress.setProgress(0);
            usagePlanText.setText("Plan: not available");
            usageAmountText.setText("Used: not available");
            usageStatusText.setText("Sign in to view usage");
            return root;
        }

        loadUsage(usageProgress, usagePlanText, usageAmountText, usageStatusText);

        updateProfileButton.setOnClickListener(v -> {
            String displayName = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();

            if (displayName.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "Display name is required", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            updateProfileButton.setEnabled(false);
            new Thread(() -> {
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("displayName", displayName);
                    JsonElement response = API.PUT("/profile", supabaseToken, body);
                    if (response == null) {
                        throw new IOException("Empty profile response");
                    }
                    JsonObject updatedUser = response.isJsonObject()
                            ? response.getAsJsonObject() : null;
                    if (updatedUser != null && updatedUser.has("user")
                            && updatedUser.get("user").isJsonObject()) {
                        updatedUser = updatedUser.getAsJsonObject("user");
                    }
                    if (updatedUser != null && updatedUser.has("id")) {
                        sessionManager.saveUser(updatedUser);
                    } else if (sessionUser != null) {
                        JsonObject metadata = sessionUser.has("user_metadata")
                                && sessionUser.get("user_metadata").isJsonObject()
                                ? sessionUser.getAsJsonObject("user_metadata") : new JsonObject();
                        metadata.addProperty("displayName", displayName);
                        sessionUser.add("user_metadata", metadata);
                        sessionManager.saveUser(sessionUser);
                    }
                    safeUi(() -> {
                        updateProfileButton.setEnabled(true);
                        Toast.success(parent, "Profile updated");
                    });
                } catch (Exception error) {
                    safeUi(() -> {
                        updateProfileButton.setEnabled(true);
                        Toast.error(parent, "Profile update failed");
                    });
                }
            }).start();
        });

        return root;
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private void performLogout() {
        new SessionManager(requireContext()).clearSession();

        Intent intent = new Intent(requireContext(), SignIn.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (parent != null) {
            parent.finish();
        }
    }

    private void applyTheme(boolean darkTheme) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_DARK_THEME, darkTheme).apply();

        AppCompatDelegate.setDefaultNightMode(
                darkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    private void loadUsage(
            ProgressBar usageProgress,
            TextView usagePlanText,
            TextView usageAmountText,
            TextView usageStatusText
    ) {
        String token = sessionManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            postUsageUnavailable(usageProgress, usagePlanText, usageAmountText, usageStatusText);
            return;
        }
            String endpoint = "/usage";

            new Thread(() -> {
                try {
                    JsonElement response = API.GET(endpoint, token);
                    if (response == null || !response.isJsonObject()) {
                        postUsageUnavailable(usageProgress, usagePlanText, usageAmountText, usageStatusText);
                        return;
                    }

                    JsonObject responseObj = response.getAsJsonObject();
                    JsonObject subscription = responseObj.has("subscription") && responseObj.get("subscription").isJsonObject()
                            ? responseObj.getAsJsonObject("subscription")
                            : null;
                    if (subscription == null) {
                        postUsageUnavailable(usageProgress, usagePlanText, usageAmountText, usageStatusText);
                        return;
                    }

                    JsonObject planObj = subscription.has("plans") && subscription.get("plans").isJsonObject()
                            ? subscription.getAsJsonObject("plans")
                            : null;

                    String planName = planObj != null && planObj.has("name") && !planObj.get("name").isJsonNull()
                            ? planObj.get("name").getAsString()
                            : "Unknown";
                    long storageLimit = responseObj.has("usage") && responseObj.get("usage").isJsonObject()
                            ? getLongValue(responseObj.getAsJsonObject("usage"), "limitBytes") : 0L;
                    Long usedBytes = responseObj.has("usage") && responseObj.get("usage").isJsonObject()
                            ? getOptionalLong(responseObj.getAsJsonObject("usage"), "usedBytes") : null;

                    safeUi(() -> {
                        usagePlanText.setText("Plan: " + planName);
                        usageProgress.setIndeterminate(false);

                        if (storageLimit <= 0L) {
                            usageProgress.setProgress(0);
                            usageAmountText.setText("Used: not available");
                            usageStatusText.setText("Storage limit unavailable");
                            return;
                        }

                        if (usedBytes == null) {
                            usageProgress.setProgress(0);
                            usageAmountText.setText("Used: unavailable / " + formatBytes(storageLimit));
                            usageStatusText.setText("Usage details are unavailable");
                            return;
                        }

                        // Backend can transiently return negative values; keep UI stable at 0.
                        long clampedUsed = Math.max(0L, usedBytes);
                        int percent = (int) Math.min(100L, (clampedUsed * 100L) / storageLimit);
                        usageProgress.setProgress(percent);
                        usageAmountText.setText("Used: " + formatGigabytes(clampedUsed)
                                + " GB / " + formatGigabytes(storageLimit) + " GB");
                        usageStatusText.setText(percent + "% used");
                    });
                } catch (IOException e) {
                    postUsageUnavailable(usageProgress, usagePlanText, usageAmountText, usageStatusText);
                }
            }).start();
    }

    private String formatGigabytes(long bytes) {
        return String.format(Locale.US, "%.2f", bytes / (1024d * 1024d * 1024d));
    }

    private void postUsageUnavailable(
            ProgressBar usageProgress,
            TextView usagePlanText,
            TextView usageAmountText,
            TextView usageStatusText
    ) {
        safeUi(() -> {
            usageProgress.setIndeterminate(false);
            usageProgress.setProgress(0);
            usagePlanText.setText("Plan: unavailable");
            usageAmountText.setText("Used: unavailable");
            usageStatusText.setText("Failed to load usage");
        });
    }

    private Long findUsedBytes(JsonObject root, JsonObject subscription) {
        // New response shape: { storage: { usedStorage: ... } }
        if (root.has("storage") && root.get("storage").isJsonObject()) {
            JsonObject storageObj = root.getAsJsonObject("storage");
            Long storageLevel = getOptionalLong(storageObj,
                    "usedStorage",
                    "usedStorageBytes",
                    "usage",
                    "usageBytes",
                    "totalUsed",
                    "totalUsage"
            );
            if (storageLevel != null) {
                return storageLevel;
            }
        }

        Long direct = getOptionalLong(root,
                "usedStorage",
                "usedStorageBytes",
                "usage",
                "usageBytes",
                "totalUsed",
                "totalUsage"
        );
        if (direct != null) {
            return direct;
        }

        Long subscriptionLevel = getOptionalLong(subscription,
                "usedStorage",
                "usedStorageBytes",
                "usage",
                "usageBytes",
                "totalUsed",
                "totalUsage"
        );
        if (subscriptionLevel != null) {
            return subscriptionLevel;
        }

        if (root.has("usage") && root.get("usage").isJsonObject()) {
            return getOptionalLong(root.getAsJsonObject("usage"),
                    "usedStorage",
                    "usedStorageBytes",
                    "usage",
                    "usageBytes",
                    "totalUsed",
                    "totalUsage"
            );
        }
        return null;
    }

    private Long getOptionalLong(JsonObject object, String... keys) {
        for (String key : keys) {
            if (!object.has(key) || object.get(key).isJsonNull()) {
                continue;
            }
            try {
                return object.get(key).getAsLong();
            } catch (Exception ignored) {
                try {
                    return Long.parseLong(object.get(key).getAsString());
                } catch (Exception ignoredAgain) {
                    // Try next key.
                }
            }
        }
        return null;
    }

    private long getLongValue(JsonObject object, String key) {
        Long value = getOptionalLong(object, key);
        return value == null ? 0L : value;
    }

    private void safeUi(Runnable action) {
        if (!isAdded() || parent == null || parent.isFinishing() || parent.isDestroyed()) {
            return;
        }
        parent.runOnUiThread(action);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024d;
        if (kb < 1024d) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024d;
        if (mb < 1024d) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        double gb = mb / 1024d;
        if (gb < 1024d) {
            return String.format(Locale.US, "%.1f GB", gb);
        }
        return String.format(Locale.US, "%.1f TB", gb / 1024d);
    }

    private int resolveThemeColor(int attr, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        boolean found = requireContext().getTheme().resolveAttribute(attr, value, true);
        if (!found) {
            return fallback;
        }
        if (value.resourceId != 0) {
            return androidx.core.content.ContextCompat.getColor(requireContext(), value.resourceId);
        }
        return value.data;
    }

}