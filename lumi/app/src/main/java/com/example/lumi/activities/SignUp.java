package com.example.lumi.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.lumi.R;

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

    }
}