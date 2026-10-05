package com.example.lumi.activities;

import android.content.SharedPreferences;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.example.lumi.R;
import com.example.lumi.lib.API;
import com.example.lumi.lib.NotificationHelper;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.lib.SupabaseNotificationRealtime;
import com.example.lumi.lib.Toast;
import com.example.lumi.fragments.AlbumsFragment;
import com.example.lumi.fragments.HomeFragment;
import com.example.lumi.fragments.MapFragment;
import com.example.lumi.fragments.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashSet;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "lumi_settings";
    private static final String KEY_DARK_THEME = "dark_theme";
    private SupabaseNotificationRealtime notificationRealtime;
    private final ExecutorService notificationExecutor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private final Set<String> displayedNotificationIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean darkTheme = prefs.getBoolean(KEY_DARK_THEME, false);
        AppCompatDelegate.setDefaultNightMode(
                darkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);
        API.initialize(this);
        NotificationHelper.createChannel(this);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        Toast.warning(this, "Notifications are disabled. Enable them in Android Settings.");
                    }
                }
        );
        notificationRealtime = new SupabaseNotificationRealtime(this, this::handleRealtimeNotification);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if(itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.nav_settings) {
                selectedFragment = new SettingsFragment();
            } else if (itemId == R.id.nav_albums) {
                selectedFragment = new AlbumsFragment();
            } else if (itemId == R.id.nav_map) {
                selectedFragment = new MapFragment();
            }

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;

        });

//        set default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new HomeFragment())
                    .commit();
        }
        handleBillingIntent(getIntent());
        loadMissedNotifications();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleBillingIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (notificationRealtime != null) notificationRealtime.connect();
        requestNotificationPermissionIfNeeded();
    }

    private void handleBillingIntent(Intent intent) {
        if (intent == null || intent.getData() == null
                || !"billing".equals(intent.getData().getHost())) {
            return;
        }

        String path = intent.getData().getPath();
        if ("/success".equals(path)) {
            Toast.success(this, "Subscription payment successful");
        } else if ("/cancelled".equals(path)) {
            Toast.warning(this, "Subscription payment canceled");
        }
        intent.setData(null);
    }

    private void handleRealtimeNotification(JsonObject record) {
        if (!record.has("id")) return;
        loadNotificationById(record.get("id").getAsString());
    }

    private void loadMissedNotifications() {
        notificationExecutor.execute(() -> {
            try {
                JsonElement response = API.GET("/notifications?limit=10", new SessionManager(this).getToken());
                if (response != null && response.isJsonObject()) {
                    JsonArray notifications = response.getAsJsonObject().getAsJsonArray("notifications");
                    if (notifications != null && !notifications.isEmpty()) {
                        for (JsonElement element : notifications) {
                            JsonObject notification = element.getAsJsonObject();
                            if (notification.has("status") && "available".equals(notification.get("status").getAsString())) {
                                showNotification(notification);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception error) {
                android.util.Log.w("MainActivity", "Could not load missed notifications", error);
            }
        });
    }

    private void loadNotificationById(String notificationId) {
        notificationExecutor.execute(() -> {
            try {
                JsonElement response = API.GET("/notifications?limit=10", new SessionManager(this).getToken());
                if (response == null || !response.isJsonObject()) return;
                JsonArray notifications = response.getAsJsonObject().getAsJsonArray("notifications");
                if (notifications == null) return;
                for (JsonElement element : notifications) {
                    JsonObject notification = element.getAsJsonObject();
                    if (notificationId.equals(notification.get("id").getAsString())
                            && (!notification.has("status")
                            || "available".equals(notification.get("status").getAsString()))) {
                        showNotification(notification);
                        return;
                    }
                }
            } catch (Exception error) {
                android.util.Log.w("MainActivity", "Could not load notification", error);
            }
        });
    }

    private void showNotification(JsonObject notification) {
        if (!notification.has("broadcast") || !notification.get("broadcast").isJsonObject()) return;
        JsonObject broadcast = notification.getAsJsonObject("broadcast");
        String title = broadcast.has("title") ? broadcast.get("title").getAsString() : "Lumi";
        String body = broadcast.has("body") ? broadcast.get("body").getAsString() : "";
        String notificationId = notification.get("id").getAsString();
        runOnUiThread(() -> {
            synchronized (displayedNotificationIds) {
                if (displayedNotificationIds.contains(notificationId)) return;
            }
            boolean displayed = NotificationHelper.show(this, notificationId, title, body);
            if (!displayed) {
                Toast.warning(this, "Notifications are disabled. Enable them in Android Settings.");
                return;
            }
            synchronized (displayedNotificationIds) {
                displayedNotificationIds.add(notificationId);
            }
            notificationExecutor.execute(() -> {
                try {
                    API.POST("/notifications/" + notificationId + "/read",
                            new SessionManager(this).getToken(), new JsonObject());
                } catch (Exception error) {
                    android.util.Log.w("MainActivity", "Could not mark notification read", error);
                }
            });
        });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onDestroy() {
        if (notificationRealtime != null) notificationRealtime.close();
        notificationExecutor.shutdownNow();
        super.onDestroy();
    }
}