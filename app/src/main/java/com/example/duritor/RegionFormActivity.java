package com.example.duritor;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionFormActivity extends DrawerActivity {

    private Spinner orchardSpinner;
    private EditText regionNameEdit;
    private EditText regionDescriptionEdit;
    private TextView charCounter;
    private Button saveRegionButton;
    private TextView cancelButton;

    private DatabaseReference orchardsRef;
    private DatabaseReference regionsRef;
    private String regionId;
    private List<String> orchardIds;
    private List<String> orchardNames;
    private ArrayAdapter<String> orchardAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        regionId = getIntent().getStringExtra("regionId");
        int titleRes = regionId != null ? R.string.title_edit_region : R.string.title_add_region;
        setupDrawerShell(R.layout.activity_region_form, R.id.nav_regions, titleRes);

        orchardSpinner = findViewById(R.id.regionOrchardSpinner);
        regionNameEdit = findViewById(R.id.regionNameEdit);
        regionDescriptionEdit = findViewById(R.id.regionDescriptionEdit);
        charCounter = findViewById(R.id.charCounter);
        saveRegionButton = findViewById(R.id.saveRegionButton);
        cancelButton = findViewById(R.id.cancelButton);

        orchardsRef = FirebaseDatabase.getInstance().getReference("orchards");
        regionsRef = FirebaseDatabase.getInstance().getReference("regions");

        orchardIds = new ArrayList<>();
        orchardNames = new ArrayList<>();
        orchardAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, orchardNames);
        orchardAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        orchardSpinner.setAdapter(orchardAdapter);

        loadOrchards();

        if (regionId != null) {
            loadRegionData();
        }

        saveRegionButton.setOnClickListener(v -> saveRegion());

        cancelButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Cancel")
                    .setMessage("Are you sure you want to cancel? Changes will not be saved.")
                    .setPositiveButton("Yes", (dialog, which) -> finish())
                    .setNegativeButton("No", null)
                    .show();
        });

        regionDescriptionEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                charCounter.setText(s.length() + "/150");
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadOrchards() {
        orchardsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                orchardIds.clear();
                orchardNames.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String orchardId = child.getKey();
                    if (orchardId == null) continue;
                    String name = child.child("name").getValue(String.class);
                    orchardIds.add(orchardId);
                    orchardNames.add(name != null && !name.isEmpty() ? name : "Unnamed Orchard");
                }
                orchardAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(RegionFormActivity.this, "Could not load orchards", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRegionData() {
        if (regionId == null) return;
        regionsRef.child(regionId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                regionNameEdit.setText(snapshot.child("name").getValue(String.class));
                regionDescriptionEdit.setText(snapshot.child("description").getValue(String.class));
                String orchardId = snapshot.child("orchardId").getValue(String.class);
                if (orchardId != null) {
                    int index = orchardIds.indexOf(orchardId);
                    if (index >= 0) orchardSpinner.setSelection(index);
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(RegionFormActivity.this, "Unable to load region", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveRegion() {
        String name = regionNameEdit.getText().toString().trim();
        String description = regionDescriptionEdit.getText().toString().trim();
        int orchardPosition = orchardSpinner.getSelectedItemPosition();

        if (name.isEmpty()) {
            Toast.makeText(this, "Enter region name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (orchardPosition < 0 || orchardPosition >= orchardIds.size()) {
            Toast.makeText(this, "Select an orchard", Toast.LENGTH_SHORT).show();
            return;
        }

        String orchardId = orchardIds.get(orchardPosition);
        String orchardName = orchardNames.get(orchardPosition);

        if (regionId == null) {
            regionId = regionsRef.push().getKey();
        }

        Map<String, Object> update = new HashMap<>();
        update.put("name", name);
        update.put("description", description);
        update.put("orchardId", orchardId);
        update.put("orchardName", orchardName);

        regionsRef.child(regionId).updateChildren(update).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(RegionFormActivity.this, "Region saved", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(RegionFormActivity.this, RegionListActivity.class));
                finish();
            } else {
                Toast.makeText(RegionFormActivity.this, "Failed to save region", Toast.LENGTH_SHORT).show();
            }
        });
    }
}