package com.example.lumi.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.lumi.R;
import com.example.lumi.lib.SupabaseAuth;
import com.example.lumi.lib.Toast;

public class SignUp extends AppCompatActivity {
    private static final String PREFS_NAME = "lumi_settings";
    private static final String KEY_DARK_THEME = "dark_theme";
    private SupabaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(prefs.getBoolean(KEY_DARK_THEME, false)
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);
        auth = new SupabaseAuth(this);

        findViewById(R.id.signUpButton).setOnClickListener(v -> signUp());
        findViewById(R.id.signInLink).setOnClickListener(v -> {
            startActivity(new Intent(this, SignIn.class));
            finish();
        });
    }

    private void signUp() {
        String email = ((TextView) findViewById(R.id.emailInput)).getText().toString().trim();
        String password = ((TextView) findViewById(R.id.passwordInput)).getText().toString();
        String confirmation = ((TextView) findViewById(R.id.confirmPasswordInput)).getText().toString();
        if (email.isEmpty() || password.isEmpty() || confirmation.isEmpty()) {
            Toast.error(this, "Please fill in all fields");
            return;
        }
        if (!password.equals(confirmation)) {
            Toast.error(this, "Passwords do not match");
            return;
        }
        auth.signUp(email, password, new SupabaseAuth.CallbackResult() {
            @Override public void onSuccess(com.google.gson.JsonObject response) {
                runOnUiThread(() -> {
                    if (response.has("access_token")) {
                        Toast.success(SignUp.this, "Account created successfully");
                        startActivity(new Intent(SignUp.this, MainActivity.class));
                    } else {
                        Toast.success(SignUp.this, "Check your email to confirm your account");
                        startActivity(new Intent(SignUp.this, SignIn.class));
                    }
                    finish();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> Toast.error(SignUp.this, message));
            }
        });
    }
}
