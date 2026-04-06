package com.example.lumi.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.lumi.R;
import com.example.lumi.lib.API;
import com.example.lumi.lib.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.JsonObject;

public class SignUp extends AppCompatActivity {

    private static final String PREFS_NAME = "lumi_settings";
    private static final String KEY_DARK_THEME = "dark_theme";

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean darkTheme = prefs.getBoolean(KEY_DARK_THEME, false);
        AppCompatDelegate.setDefaultNightMode(
                darkTheme ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        TextView signInLink = findViewById(R.id.signInLink);
        signInLink.setOnClickListener(v -> {
            // navigate to sign in activity
            startActivity(new android.content.Intent(SignUp.this, SignIn.class));
            finish();
        });

        Button signUpButton = findViewById(R.id.signUpButton);
        signUpButton.setOnClickListener(v -> {
            handleSignUp();
        });
    }

    private void safeUi(Runnable r) {
        if (!isFinishing() && !isDestroyed()) {
            runOnUiThread(r);
        }
    }

    private void handleSignUp() {
        TextView emailField = findViewById(R.id.emailInput);
        TextView passwordField = findViewById(R.id.passwordInput);
        TextView confirmPasswordField = findViewById(R.id.confirmPasswordInput);

        String email = emailField.getText().toString();
        String password = passwordField.getText().toString();
        String confirmPassword = confirmPasswordField.getText().toString();

        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.error(SignUp.this, "Please fill in all fields");
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.error(SignUp.this, "Passwords do not match");
            return;
        }

        new Thread(() -> {
            try {
                JsonObject reqObj = new JsonObject();
                reqObj.addProperty("email", email);
                reqObj.addProperty("password", password);

                registerUser(email, password);
//                JsonObject responseObj = API.POST("/auth/register", reqObj);
//                Log.i("SignUp", "Response: " + responseObj.toString());

//                if(responseObj.get("success").getAsBoolean()) {
//                    safeUi(() -> {
//                        Toast.success(SignUp.this, "Account created successfully");
//                        startActivity(new android.content.Intent(SignUp.this, SignIn.class));
//                    });
//                } else {
//                    String errorMessage = responseObj.has("message") ? responseObj.get("message").getAsString() : "An error occurred";
//                    safeUi(() -> Toast.error(SignUp.this, errorMessage));
//                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.i("SignUp", e.getMessage());
            }
        }).start();

    }

    private void registerUser(String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Registration successful
                            Log.d("Auth", "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();

                            // Show success message
                            android.widget.Toast.makeText(SignUp.this,
                                    "Registration successful!", android.widget.Toast.LENGTH_SHORT).show();

                            // Navigate to main activity
                            startActivity(new Intent(SignUp.this, MainActivity.class));
                            finish();

                        } else {
                            // Registration failed
                            Log.w("Auth", "createUserWithEmail:failure", task.getException());
                            android.widget.Toast.makeText(SignUp.this,
                                    "Registration failed: " + task.getException().getMessage(),
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

}