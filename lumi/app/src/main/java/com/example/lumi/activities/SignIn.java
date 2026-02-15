package com.example.lumi.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;

public class SignIn extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_in);

        Button submitButton = findViewById(R.id.signInButton);
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginButtonOnClick();
            }
        });

        TextView signUpLink = findViewById(R.id.signUpLink);
        signUpLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                navigate to sign up activity
                startActivity(new android.content.Intent(SignIn.this, SignUp.class));
            }
        });
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
                try {

                    JsonObject reqObj = new JsonObject();
                    reqObj.addProperty("email", email);
                    reqObj.addProperty("password", password);

                    JsonObject responseObj = API.POST("/login", reqObj);
                    Log.i("API", "Login successful, message: " + responseObj.toString());

                    if (responseObj.get("success").getAsBoolean()) {
                        safeUi(() -> Toast.success(SignIn.this, responseObj.get("message").getAsString()));
                    } else {
                        safeUi(() -> Toast.error(SignIn.this, responseObj.get("message").getAsString()));
                    }

                } catch (IOException e) {
                    safeUi(() -> Toast.error(SignIn.this, "Login Failed : Network Error"));
                } catch (IllegalStateException e) {
                    safeUi(() -> Toast.error(SignIn.this, "Login Failed : Invalid Response"));
                }
            }).start();
        }
    }

}