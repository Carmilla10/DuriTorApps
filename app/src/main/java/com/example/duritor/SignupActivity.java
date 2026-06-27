package com.example.duritor;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class SignupActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword, etConfirmPassword;
    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirmPassword;
    private Button btnSignUp;
    private TextView tvLoginLink, tvBack;

    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("users");

        tilName = findViewById(R.id.tilName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        etName = findViewById(R.id.signupName);
        etEmail = findViewById(R.id.signupEmail);
        etPassword = findViewById(R.id.signupPassword);
        etConfirmPassword = findViewById(R.id.signupConfirmPassword);

        btnSignUp = findViewById(R.id.signupButton);
        tvLoginLink = findViewById(R.id.loginLink);
        tvBack = findViewById(R.id.backButton);

        tvBack.setOnClickListener(v -> finish());

        tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });

        btnSignUp.setOnClickListener(v -> validateAndSignUp());
    }

    private void validateAndSignUp() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        boolean isValid = true;

        if (TextUtils.isEmpty(name)) {
            tilName.setError("Full name is required");
            etName.requestFocus();
            isValid = false;
        } else {
            tilName.setError(null);
        }

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            etEmail.requestFocus();
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email");
            etEmail.requestFocus();
            isValid = false;
        } else {
            tilEmail.setError(null);
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            etPassword.requestFocus();
            isValid = false;
        } else if (password.length() < 8) {
            tilPassword.setError("Password must be at least 8 characters");
            etPassword.requestFocus();
            isValid = false;
        } else {
            tilPassword.setError(null);
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            tilConfirmPassword.setError("Please confirm your password");
            etConfirmPassword.requestFocus();
            isValid = false;
        } else if (!confirmPassword.equals(password)) {
            tilConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            isValid = false;
        } else {
            tilConfirmPassword.setError(null);
        }

        if (isValid) {
            performSignUp(name, email, password);
        }
    }

    private void performSignUp(String name, String email, String password) {
        btnSignUp.setEnabled(false);
        btnSignUp.setText("Creating...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        String uid = task.getResult().getUser().getUid();
                        databaseReference.child(uid).child("displayName").setValue(name);
                        databaseReference.child(uid).child("email").setValue(email);

                        Toast.makeText(this, "Signup Successful!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(SignupActivity.this, LoginActivity.class));
                        finish();
                    } else {
                        btnSignUp.setEnabled(true);
                        btnSignUp.setText("Sign Up");
                        Toast.makeText(this, "Signup Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}