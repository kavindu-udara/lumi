package com.example.lumi.activities;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.example.lumi.lib.SessionManager;
import com.example.lumi.lib.Toast;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;

public class SignIn extends AppCompatActivity {

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
        setContentView(R.layout.activity_sign_in);

//        init firebase auth
        mAuth = FirebaseAuth.getInstance();

//        TODO : Commented for now, will implement later
        Button submitButton = findViewById(R.id.signInButton);
        submitButton.setOnClickListener(v -> {
            loginButtonOnClick();
        });
//
        TextView signUpLink = findViewById(R.id.signUpLink);
        signUpLink.setOnClickListener(v -> {
//                navigate to sign up activity
            startActivity(new android.content.Intent(SignIn.this, SignUp.class));
        });
    }

    @Override
    public void onStart(){
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null){
            // User is logged in - redirect to MainActivity
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    private void safeUi(Runnable r) {
        if (!isFinishing() && !isDestroyed()) {
            runOnUiThread(r);
        }
    }

    private void loginButtonOnClick() {
        TextView emailField = findViewById(R.id.emailInput);
        TextView passwordField = findViewById(R.id.passwordInput);

        String email = emailField.getText().toString();
        String password = passwordField.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.error(SignIn.this, "Please fill in all fields");
        } else {
//                    send login request to api
            new Thread(() -> {
//                try {

                    loginUser(email, password);
//                    JsonObject reqObj = new JsonObject();
//                    reqObj.addProperty("email", email);
//                    reqObj.addProperty("password", password);

//                    JsonObject responseObj = API.POST("/auth/login", reqObj);
//                    Log.i("SignIn", "Login Response, message: " + responseObj.toString());

//                    if (responseObj.get("success").getAsBoolean()) {
////                        Store token and user data in session manager
//                        SessionManager sessionManager = new SessionManager(SignIn.this);
//                        sessionManager.saveToken(responseObj.get("token").getAsString());
//                        sessionManager.saveUser(responseObj.getAsJsonObject("user"));
//
//                        safeUi(() -> Toast.success(SignIn.this, responseObj.get("message").getAsString()));
//                        startActivity(new android.content.Intent(SignIn.this, MainActivity.class));
//                        finish();
//
//                    } else {
//                        safeUi(() -> Toast.error(SignIn.this, responseObj.get("message").getAsString()));
//                    }

//                } catch (IOException e) {
//                    e.printStackTrace();
//                    safeUi(() -> Toast.error(SignIn.this, "Login Failed : Network Error"));
//                } catch (IllegalStateException e) {
//                    e.printStackTrace();
//                    safeUi(() -> Toast.error(SignIn.this, "Login Failed : Invalid Response"));
//                }
            }).start();
        }
    }

    private void loginUser(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Login successful
                            Log.d("Auth", "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();

                            android.widget.Toast.makeText(SignIn.this,
                                    "Login successful!", android.widget.Toast.LENGTH_SHORT).show();

                            // Navigate to main activity
                            startActivity(new Intent(SignIn.this, MainActivity.class));
                            finish();

                        } else {
                            // Login failed
                            Log.w("Auth", "signInWithEmail:failure", task.getException());
                            android.widget.Toast.makeText(SignIn.this,
                                    "Login failed: " + task.getException().getMessage(),
                                    android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

}