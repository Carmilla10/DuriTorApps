package com.example.duritor;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private TextInputLayout tilEmail, tilPassword;
    private Button btnLogin;
    private TextView tvSignupLink, tvForgotPassword;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // Initialize views
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.loginEmail);
        etPassword = findViewById(R.id.loginPassword);
        btnLogin = findViewById(R.id.loginButton);
        tvSignupLink = findViewById(R.id.signupLink);
        tvForgotPassword = findViewById(R.id.forgotPassword);

        // Sign up link - go to SignupActivity
        tvSignupLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
            finish();
        });

        // Forgot password - WORKING
        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

        // Login button
        btnLogin.setOnClickListener(v -> validateAndLogin());
    }

    private void showForgotPasswordDialog() {
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "Please enter your email address first", Toast.LENGTH_SHORT).show();
            etEmail.requestFocus();
            return;
        }

        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("We will send a password reset link to:\n\n" + email + "\n\nDo you want to continue?")
                .setPositiveButton("Send", (dialog, which) -> sendPasswordResetEmail(email))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void sendPasswordResetEmail(String email) {
        // Show loading state
        tvForgotPassword.setEnabled(false);
        tvForgotPassword.setText("Sending...");

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    tvForgotPassword.setEnabled(true);
                    tvForgotPassword.setText("Forgot password?");

                    if (task.isSuccessful()) {
                        // Show success message with instructions
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Email Sent")
                                .setMessage("Password reset email has been sent to:\n\n" + email +
                                        "\n\nPlease check your inbox and follow the instructions to reset your password.")
                                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                                .show();
                    } else {
                        String errorMessage = "Failed to send reset email";
                        if (task.getException() != null) {
                            String exceptionMsg = task.getException().getMessage();
                            if (exceptionMsg != null && exceptionMsg.contains("There is no user record")) {
                                errorMessage = "No account found with this email address";
                            } else if (exceptionMsg != null) {
                                errorMessage = exceptionMsg;
                            }
                        }
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void validateAndLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        boolean isValid = true;

        // Validate Email
        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email is required");
            etEmail.requestFocus();
            isValid = false;
        } else {
            tilEmail.setError(null);
        }

        // Validate Password
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password is required");
            etPassword.requestFocus();
            isValid = false;
        } else {
            tilPassword.setError(null);
        }

        if (isValid) {
            performLogin(email, password);
        }
    }

    private void performLogin(String email, String password) {
        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Log In");

                        String errorMessage = "Login Failed";
                        if (task.getException() != null) {
                            String exceptionMsg = task.getException().getMessage();
                            if (exceptionMsg != null) {
                                if (exceptionMsg.contains("The password is invalid")) {
                                    errorMessage = "Invalid password. Please try again.";
                                } else if (exceptionMsg.contains("There is no user record")) {
                                    errorMessage = "No account found with this email.";
                                } else {
                                    errorMessage = exceptionMsg;
                                }
                            }
                        }
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }
}