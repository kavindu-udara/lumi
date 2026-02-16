package com.example.lumi.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.lumi.R;
import com.example.lumi.lib.API;
import com.example.lumi.lib.Toast;
import com.google.gson.JsonObject;

public class SignUp extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        TextView signInLink = findViewById(R.id.signInLink);
        signInLink.setOnClickListener(v -> {
            // navigate to sign in activity
            startActivity(new android.content.Intent(SignUp.this, SignIn.class));
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

    private void handleSignUp(){
        TextView emailField = findViewById(R.id.emailInput);
        TextView passwordField = findViewById(R.id.passwordInput);
        TextView confirmPasswordField = findViewById(R.id.confirmPasswordInput);

        String email = emailField.getText().toString();
        String password = passwordField.getText().toString();
        String confirmPassword = confirmPasswordField.getText().toString();

        if(email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.error(SignUp.this, "Please fill in all fields");
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.error(SignUp.this, "Passwords do not match");
            return;
        }

        new Thread(() -> {
            try{
                JsonObject reqObj = new JsonObject();
                reqObj.addProperty("email", email);
                reqObj.addProperty("password", password);

                JsonObject responseObj = API.POST("/auth/register", reqObj);
                Log.i("SignUp", "Response: " + responseObj.toString());

                if(responseObj.get("success").getAsBoolean()) {
                    safeUi(() -> {
                        Toast.success(SignUp.this, "Account created successfully");
                        startActivity(new android.content.Intent(SignUp.this, SignIn.class));
                    });
                } else {
                    String errorMessage = responseObj.has("message") ? responseObj.get("message").getAsString() : "An error occurred";
                    safeUi(() -> Toast.error(SignUp.this, errorMessage));
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.i("SignUp", e.getMessage() );
            }
        }).start();

    }
}