package com.example.duritor;

import static android.content.Intent.getIntent;
import static androidx.core.content.ContextCompat.startActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class OrchardFormActivity extends DrawerActivity {

    private EditText etName, etLat, etLng, etDescription;
    private Button btnSave;
    private TextView btnCancel;
    private DatabaseReference orchardRef;
    private String orchardId;
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        orchardId = getIntent().getStringExtra("orchardId");
        isEditMode = orchardId != null;
        int titleRes = isEditMode ? R.string.title_edit_orchard : R.string.title_add_orchard;

        setupDrawerShell(R.layout.activity_orchard_form, R.id.nav_orchards, titleRes);

        // REMOVED etRegion from findViewByID
        etName = findViewById(R.id.orchardNameEdit);
        etLat = findViewById(R.id.orchardLatEdit);
        etLng = findViewById(R.id.orchardLngEdit);
        etDescription = findViewById(R.id.orchardDescriptionEdit);
        btnSave = findViewById(R.id.saveOrchardButton);
        btnCancel = findViewById(R.id.cancelButton);

        orchardRef = FirebaseDatabase.getInstance().getReference("orchards");

        if (isEditMode) {
            btnSave.setText("Update Orchard");
            loadOrchardData();
        }

        btnSave.setOnClickListener(v -> saveOrchard());

        btnCancel.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Cancel")
                    .setMessage("Are you sure you want to cancel? Changes will not be saved.")
                    .setPositiveButton("Yes", (dialog, which) -> finish())
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    private void loadOrchardData() {
        orchardRef.child(orchardId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(OrchardFormActivity.this, "Orchard not found", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                etName.setText(snapshot.child("name").getValue(String.class));
                etLat.setText(snapshot.child("lat").getValue(String.class));
                etLng.setText(snapshot.child("lng").getValue(String.class));
                etDescription.setText(snapshot.child("description").getValue(String.class));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(OrchardFormActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveOrchard() {
        String name = etName.getText().toString().trim();
        String lat = etLat.getText().toString().trim();
        String lng = etLng.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Orchard name is required");
            etName.requestFocus();
            return;
        }

        if (lat.isEmpty()) {
            etLat.setError("Latitude is required");
            etLat.requestFocus();
            return;
        }

        if (lng.isEmpty()) {
            etLng.setError("Longitude is required");
            etLng.requestFocus();
            return;
        }

        if (orchardId == null) {
            orchardId = orchardRef.push().getKey();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("description", description);

        String locationString = lat + ", " + lng;
        data.put("location", locationString);

        if (!isEditMode) {
            String createdAt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
            data.put("createdAt", createdAt);
        }

        orchardRef.child(orchardId).updateChildren(data).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(OrchardFormActivity.this,
                        isEditMode ? "Orchard updated successfully!" : "Orchard saved successfully!", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(OrchardFormActivity.this, OrchardListActivity.class));
                finish();
            } else {
                Toast.makeText(OrchardFormActivity.this, "Failed to save orchard", Toast.LENGTH_SHORT).show();
            }
        });
    }
}