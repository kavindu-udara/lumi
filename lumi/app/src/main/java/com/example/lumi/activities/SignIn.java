package com.example.lumi.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.lumi.R;
import com.example.lumi.lib.SessionManager;
import com.example.lumi.lib.SupabaseAuth;
import com.example.lumi.lib.Toast;

public class SignIn extends AppCompatActivity {
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
        setContentView(R.layout.activity_sign_in);
        auth = new SupabaseAuth(this);

        findViewById(R.id.signInButton).setOnClickListener(v -> signIn());
        findViewById(R.id.signUpLink).setOnClickListener(v ->
                startActivity(new Intent(this, SignUp.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (new SessionManager(this).isLoggedIn()) {
            openMain();
        }
    }

    private void signIn() {
        String email = ((TextView) findViewById(R.id.emailInput)).getText().toString().trim();
        String password = ((TextView) findViewById(R.id.passwordInput)).getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.error(this, "Please fill in all fields");
            return;
        }
        auth.signIn(email, password, new SupabaseAuth.CallbackResult() {
            @Override public void onSuccess(com.google.gson.JsonObject response) {
                runOnUiThread(() -> { Toast.success(SignIn.this, "Login successful"); openMain(); });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> Toast.error(SignIn.this, message));
            }
        });
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
