package com.example.lumi.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.lumi.R;
import com.example.lumi.lib.Toast;

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
                TextView emailField = findViewById(R.id.emailInput);
                TextView passwordField = findViewById(R.id.passwordInput);

                String email = emailField.getText().toString();
                String password = passwordField.getText().toString();

                if(email.isEmpty() || password.isEmpty()) {
                    Toast.error(SignIn.this, "Please fill in all fields");
                } else {
                    // Simulate successful sign-in
                    Toast.success(SignIn.this, "Sign-in successful!");
                }
            }
        });

    }
}