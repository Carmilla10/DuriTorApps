package com.example.duritor;

import android.content.Intent;
import android.os.Bundle;
import android.widget.AdapterView;
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

public class TreeFormActivity extends DrawerActivity {

    private Spinner treeOrchardSpinner;
    private Spinner treeRegionSpinner;
    private EditText treeIdEdit;
    private EditText treeVarietyEdit;
    private EditText treeNotesEdit;
    private Button saveTreeButton;
    private TextView cancelButton;
    private DatabaseReference orchardsRef;
    private DatabaseReference regionsRef;
    private DatabaseReference treesRef;
    private String treeId;
    private String treeRegionId;
    private boolean treeLoaded = false;
    private List<String> orchardIds;
    private List<String> orchardNames;
    private List<String> regionIds;
    private List<String> regionNames;
    private List<String> regionOrchardIds;
    private List<String> filteredRegionIds;
    private List<String> filteredRegionNames;
    private ArrayAdapter<String> orchardAdapter;
    private ArrayAdapter<String> regionAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        treeId = getIntent().getStringExtra("treeId");
        int titleRes = treeId != null ? R.string.title_edit_tree : R.string.title_add_tree;
        setupDrawerShell(R.layout.activity_tree_form, R.id.nav_trees, titleRes);

        treeOrchardSpinner = findViewById(R.id.treeOrchardSpinner);
        treeRegionSpinner = findViewById(R.id.treeRegionSpinner);
        treeIdEdit = findViewById(R.id.treeIdEdit);
        treeVarietyEdit = findViewById(R.id.treeVarietyEdit);
        treeNotesEdit = findViewById(R.id.treeNotesEdit);
        saveTreeButton = findViewById(R.id.saveTreeButton);
        cancelButton = findViewById(R.id.cancelButton);

        orchardsRef = FirebaseDatabase.getInstance().getReference("orchards");
        regionsRef = FirebaseDatabase.getInstance().getReference("regions");
        treesRef = FirebaseDatabase.getInstance().getReference("trees");

        orchardIds = new ArrayList<>();
        orchardNames = new ArrayList<>();
        regionIds = new ArrayList<>();
        regionNames = new ArrayList<>();
        regionOrchardIds = new ArrayList<>();
        filteredRegionIds = new ArrayList<>();
        filteredRegionNames = new ArrayList<>();

        orchardAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, orchardNames);
        orchardAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        treeOrchardSpinner.setAdapter(orchardAdapter);

        regionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, filteredRegionNames);
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        treeRegionSpinner.setAdapter(regionAdapter);

        treeOrchardSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                filterRegionsForOrchard(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                filterRegionsForOrchard(0);
            }
        });

        cancelButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Cancel")
                    .setMessage("Are you sure you want to cancel? Changes will not be saved.")
                    .setPositiveButton("Yes", (dialog, which) -> finish())
                    .setNegativeButton("No", null)
                    .show();
        });

        loadOrchards();
        loadRegions();

        if (treeId != null) {
            loadTree();
        }

        saveTreeButton.setOnClickListener(v -> saveTree());
    }

    private void loadRegions() {
        regionsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                regionIds.clear();
                regionNames.clear();
                regionOrchardIds.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String regionId = child.getKey();
                    String name = child.child("name").getValue(String.class);
                    String orchardId = child.child("orchardId").getValue(String.class);
                    regionIds.add(regionId);
                    regionNames.add(name != null && !name.isEmpty() ? name : "Unnamed Region");
                    regionOrchardIds.add(orchardId != null ? orchardId : "");
                }
                int selectedOrchardPosition = treeOrchardSpinner.getSelectedItemPosition();
                filterRegionsForOrchard(selectedOrchardPosition);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(TreeFormActivity.this, "Could not load regions", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterRegionsForOrchard(int orchardPosition) {
        filteredRegionIds.clear();
        filteredRegionNames.clear();

        if (orchardPosition < 0 || orchardPosition >= orchardIds.size()) {
            filteredRegionIds.add("");
            filteredRegionNames.add("Select orchard first");
        } else {
            String orchardId = orchardIds.get(orchardPosition);
            for (int i = 0; i < regionIds.size(); i++) {
                if (orchardId.equals(regionOrchardIds.get(i))) {
                    filteredRegionIds.add(regionIds.get(i));
                    filteredRegionNames.add(regionNames.get(i));
                }
            }
            if (filteredRegionIds.isEmpty()) {
                filteredRegionIds.add("");
                filteredRegionNames.add("No regions available");
            }
        }

        regionAdapter.notifyDataSetChanged();
        if (treeLoaded && treeRegionId != null && !treeRegionId.isEmpty()) {
            int index = filteredRegionIds.indexOf(treeRegionId);
            if (index >= 0) {
                treeRegionSpinner.setSelection(index);
            }
        }
    }

    private void loadOrchards() {
        orchardsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                orchardIds.clear();
                orchardNames.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String orchardId = child.getKey();
                    String name = child.child("name").getValue(String.class);
                    orchardIds.add(orchardId);
                    if (name == null || name.isEmpty()) {
                        name = "Unnamed Orchard";
                    }
                    orchardNames.add(name);
                }
                orchardAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(TreeFormActivity.this, "Could not load orchards", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTree() {
        if (treeId == null) return;
        treesRef.child(treeId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                treeIdEdit.setText(snapshot.child("treeId").getValue(String.class));
                treeVarietyEdit.setText(snapshot.child("durianVariety").getValue(String.class));
                treeNotesEdit.setText(snapshot.child("notes").getValue(String.class));
                treeRegionId = snapshot.child("regionId").getValue(String.class);
                String orchardId = snapshot.child("orchardId").getValue(String.class);
                if (orchardId != null) {
                    int index = orchardIds.indexOf(orchardId);
                    if (index >= 0) {
                        treeOrchardSpinner.setSelection(index);
                    }
                }
                treeLoaded = true;
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(TreeFormActivity.this, "Could not load tree", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveTree() {
        String treeCode = treeIdEdit.getText().toString().trim();
        String variety = treeVarietyEdit.getText().toString().trim();
        String notes = treeNotesEdit.getText().toString().trim();
        int orchardPosition = treeOrchardSpinner.getSelectedItemPosition();

        if (treeCode.isEmpty()) {
            Toast.makeText(this, "Enter tree ID", Toast.LENGTH_SHORT).show();
            return;
        }
        if (variety.isEmpty()) {
            Toast.makeText(this, "Enter durian variety", Toast.LENGTH_SHORT).show();
            return;
        }
        if (orchardPosition < 0 || orchardPosition >= orchardIds.size()) {
            Toast.makeText(this, "Select an orchard", Toast.LENGTH_SHORT).show();
            return;
        }
        int regionPosition = treeRegionSpinner.getSelectedItemPosition();
        if (regionPosition < 0 || regionPosition >= filteredRegionIds.size() || filteredRegionIds.get(regionPosition).isEmpty()) {
            Toast.makeText(this, "Select a region", Toast.LENGTH_SHORT).show();
            return;
        }

        String orchardId = orchardIds.get(orchardPosition);
        String orchardName = orchardNames.get(orchardPosition);
        String regionId = filteredRegionIds.get(regionPosition);
        String regionName = filteredRegionNames.get(regionPosition);

        if (treeId == null) {
            treeId = treesRef.push().getKey();
        }

        Map<String, Object> update = new HashMap<>();
        update.put("treeId", treeCode);
        update.put("name", treeCode);
        update.put("durianVariety", variety);
        update.put("notes", notes);
        update.put("orchardId", orchardId);
        update.put("orchardName", orchardName);
        update.put("regionId", regionId);
        update.put("regionName", regionName);

        treesRef.child(treeId).updateChildren(update).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(TreeFormActivity.this, "Tree saved", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(TreeFormActivity.this, TreeListActivity.class));
                finish();
            } else {
                Toast.makeText(TreeFormActivity.this, "Could not save tree", Toast.LENGTH_SHORT).show();
            }
        });
    }
}