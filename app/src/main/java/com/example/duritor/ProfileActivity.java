package com.example.duritor;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends DrawerActivity {

    private TextView profileEmailText;
    private TextView profileNameText;
    private TextView profileUsernameText;
    private Button saveProfileButton;
    private Button changePasswordButton;
    private TextView logoutButton;
    private FirebaseAuth mAuth;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDrawerShell(R.layout.activity_profile, R.id.nav_profile, R.string.title_profile);

        profileEmailText = findViewById(R.id.profileEmailText);
        profileNameText = findViewById(R.id.profileNameText);
        profileUsernameText = findViewById(R.id.profileUsernameText);
        saveProfileButton = findViewById(R.id.saveProfileButton);
        changePasswordButton = findViewById(R.id.changePasswordButton);
        logoutButton = findViewById(R.id.logoutButton);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
            return;
        }

        profileEmailText.setText(user.getEmail());
        usersRef = FirebaseDatabase.getInstance().getReference("users").child(user.getUid());
        loadUserProfile();

        saveProfileButton.setOnClickListener(v -> showEditProfileDialog());
        changePasswordButton.setOnClickListener(v -> showPasswordDialog());
        logoutButton.setOnClickListener(v -> showLogoutDialog());
    }

    private void loadUserProfile() {
        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String fullName = snapshot.child("fullName").getValue(String.class);
                String username = snapshot.child("username").getValue(String.class);

                if (fullName != null) profileNameText.setText(fullName);
                if (username != null) profileUsernameText.setText("@" + username);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEditProfileDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Full Name");
        nameInput.setText(profileNameText.getText().toString());
        layout.addView(nameInput);

        final EditText usernameInput = new EditText(this);
        usernameInput.setHint("Username");
        String currentUsername = profileUsernameText.getText().toString();
        if (currentUsername.startsWith("@")) {
            currentUsername = currentUsername.substring(1);
        }
        usernameInput.setText(currentUsername);
        layout.addView(usernameInput);

        new AlertDialog.Builder(this)
                .setTitle("Edit Profile")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = nameInput.getText().toString().trim();
                    String newUsername = usernameInput.getText().toString().trim();

                    if (!newName.isEmpty() && !newUsername.isEmpty()) {
                        profileNameText.setText(newName);
                        profileUsernameText.setText("@" + newUsername);
                        saveProfileToFirebase(newName, newUsername);
                    } else {
                        Toast.makeText(ProfileActivity.this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveProfileToFirebase(String fullName, String username) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("fullName", fullName);
        updates.put("username", username);

        usersRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                refreshDrawerHeader();
            } else {
                Toast.makeText(ProfileActivity.this, "Could not update profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPasswordDialog() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setHint("New Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setView(passwordInput)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newPassword = passwordInput.getText().toString().trim();
                    if (newPassword.length() < 6) {
                        Toast.makeText(ProfileActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        user.updatePassword(newPassword).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(ProfileActivity.this, "Password updated", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(ProfileActivity.this, "Password update failed", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    protected void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}