package com.example.duritor;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public abstract class DrawerActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private int selectedMenuId;

    protected void setupDrawerShell(@LayoutRes int contentLayoutId, int selectedMenuId, @StringRes int titleRes) {
        setContentView(R.layout.activity_drawer_shell);
        getLayoutInflater().inflate(contentLayoutId, findViewById(R.id.content_frame), true);
        setupDrawer(selectedMenuId, getString(titleRes));
    }

    protected void setupDrawer(int selectedMenuId, String title) {
        this.selectedMenuId = selectedMenuId;
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.drawer_open,
                R.string.drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setCheckedItem(selectedMenuId);
        updateNavHeader(); // Load display name from Firebase Realtime Database

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else if (!getClass().equals(MainActivity.class)) {
                    Intent intent = new Intent(DrawerActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_logout) {
                drawerLayout.closeDrawer(GravityCompat.START);
                showLogoutDialog();
                return true;
            }
            if (itemId == selectedMenuId) {
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
            navigateTo(itemId);
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void updateNavHeader() {
        View header = navigationView.getHeaderView(0);
        TextView nameView = header.findViewById(R.id.navHeaderName);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            String uid = user.getUid();
            DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users").child(uid);

            // Load display name from Firebase Realtime Database
            usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    String displayName = snapshot.child("displayName").getValue(String.class);

                    if (displayName != null && !displayName.isEmpty()) {
                        // Show the display name that user set in Profile
                        nameView.setText(displayName);
                    } else {
                        // Fallback to email username if no display name set
                        String email = user.getEmail();
                        if (email != null && !email.isEmpty()) {
                            String nameFromEmail = email.split("@")[0];
                            nameView.setText(nameFromEmail);
                        } else {
                            nameView.setText("User");
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    // Fallback to email if database read fails
                    String email = user.getEmail();
                    if (email != null && !email.isEmpty()) {
                        String nameFromEmail = email.split("@")[0];
                        nameView.setText(nameFromEmail);
                    } else {
                        nameView.setText("User");
                    }
                }
            });
        } else {
            nameView.setText("Guest");
        }
    }

    // Public method to refresh the drawer header (call this after profile update)
    public void refreshDrawerHeader() {
        updateNavHeader();
    }

    private void navigateTo(int menuId) {
        Class<?> target = null;
        if (menuId == R.id.nav_dashboard) {
            target = MainActivity.class;
        } else if (menuId == R.id.nav_history) {
            target = HistoryActivity.class;
        } else if (menuId == R.id.nav_analytics) {
            target = AnalyticsActivity.class;
        } else if (menuId == R.id.nav_map) {
            target = MapActivity.class;
        } else if (menuId == R.id.nav_orchards) {
            target = OrchardListActivity.class;
        } else if (menuId == R.id.nav_trees) {
            target = TreeListActivity.class;
        } else if (menuId == R.id.nav_regions) {
            target = RegionListActivity.class;
        } else if (menuId == R.id.nav_profile) {
            target = ProfileActivity.class;
        } else if (menuId == R.id.nav_about) {
            target = AboutActivity.class;
        }
        if (target == null || target == getClass()) {
            return;
        }
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    protected void showLogoutDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> logout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    protected void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}