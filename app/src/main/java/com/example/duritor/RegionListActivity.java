package com.example.duritor;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionListActivity extends DrawerActivity {

    private ListView regionListView;
    private EditText searchEditText;
    private FloatingActionButton addRegionButton;

    private DatabaseReference regionRef;
    private DatabaseReference treesRef;
    private List<RegionItem> masterRegionList;
    private List<RegionItem> filteredRegionList;
    private RegionAdapter adapter;
    private Map<String, Integer> regionTreeCounts;
    private Map<String, String> regionTreeDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDrawerShell(R.layout.activity_region_list, R.id.nav_regions, R.string.title_regions);

        regionListView = findViewById(R.id.regionListView);
        searchEditText = findViewById(R.id.searchEditText);
        addRegionButton = findViewById(R.id.addRegionButton);

        masterRegionList = new ArrayList<>();
        filteredRegionList = new ArrayList<>();
        adapter = new RegionAdapter();
        regionListView.setAdapter(adapter);

        regionRef = FirebaseDatabase.getInstance().getReference("regions");
        treesRef = FirebaseDatabase.getInstance().getReference("trees");
        regionTreeCounts = new HashMap<>();
        regionTreeDetails = new HashMap<>();

        addRegionButton.setOnClickListener(v ->
                startActivity(new Intent(RegionListActivity.this, RegionFormActivity.class))
        );

        loadRegions();
        loadTreeCounts();

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadRegions() {
        regionRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                masterRegionList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String id = child.getKey();
                    String name = child.child("name").getValue(String.class);
                    String description = child.child("description").getValue(String.class);
                    String orchardName = child.child("orchardName").getValue(String.class);

                    RegionItem item = new RegionItem();
                    item.id = id;
                    item.name = name != null ? name : "Unnamed Region";
                    item.orchardName = orchardName != null ? orchardName : "";
                    item.description = description != null ? description : "";
                    item.treeCount = regionTreeCounts.getOrDefault(id, 0);
                    item.treeDetails = regionTreeDetails.getOrDefault(id, "No trees available");

                    masterRegionList.add(item);
                }
                filterList(searchEditText.getText().toString());
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(RegionListActivity.this, "Failed to load regions", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void filterList(String query) {
        filteredRegionList.clear();
        if (query.isEmpty()) {
            filteredRegionList.addAll(masterRegionList);
        } else {
            for (RegionItem item : masterRegionList) {
                if (item.name.toLowerCase().contains(query.toLowerCase()) ||
                        item.orchardName.toLowerCase().contains(query.toLowerCase()) ||
                        item.description.toLowerCase().contains(query.toLowerCase())) {
                    filteredRegionList.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void loadTreeCounts() {
        treesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                regionTreeCounts.clear();
                regionTreeDetails.clear();
                Map<String, List<String>> tempDetails = new HashMap<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String regionId = child.child("regionId").getValue(String.class);
                    if (regionId == null) continue;
                    regionTreeCounts.put(regionId, regionTreeCounts.getOrDefault(regionId, 0) + 1);

                    String treeId = child.child("treeId").getValue(String.class);
                    if (treeId == null || treeId.isEmpty()) {
                        treeId = child.getKey();
                    }
                    String detail = treeId != null ? treeId : "?";
                    tempDetails.computeIfAbsent(regionId, k -> new ArrayList<>()).add(detail);
                }

                for (Map.Entry<String, List<String>> entry : tempDetails.entrySet()) {
                    List<String> details = entry.getValue();
                    if (details.size() <= 3) {
                        regionTreeDetails.put(entry.getKey(), String.join(", ", details));
                    } else {
                        regionTreeDetails.put(entry.getKey(), String.join(", ", details.subList(0, 3)) + ", ...and " + details.size() + " more");
                    }
                }

                for (RegionItem item : masterRegionList) {
                    item.treeCount = regionTreeCounts.getOrDefault(item.id, 0);
                    item.treeDetails = regionTreeDetails.getOrDefault(item.id, "No trees available");
                }
                filterList(searchEditText.getText().toString());
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(RegionListActivity.this, "Failed to load tree counts", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDelete(String regionId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Region")
                .setMessage("Are you sure you want to delete this region?")
                .setPositiveButton("Delete", (dialog, which) -> deleteRegion(regionId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRegion(String regionId) {
        regionRef.child(regionId).removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(RegionListActivity.this, "Region deleted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(RegionListActivity.this, "Delete failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class RegionAdapter extends BaseAdapter {
        @Override
        public int getCount() { return filteredRegionList.size(); }
        @Override
        public Object getItem(int position) { return filteredRegionList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(RegionListActivity.this)
                        .inflate(R.layout.region_item, parent, false);
            }

            RegionItem item = filteredRegionList.get(position);

            // 1. Bold Title: Region + Orchard
            TextView nameText = convertView.findViewById(R.id.regionNameText);
            nameText.setText(item.name + " - " + item.orchardName);

            // 2. Tree IDs (First after title)
            TextView detailsText = convertView.findViewById(R.id.regionTreeDetailsText);
            detailsText.setText(item.treeDetails);

            // 3. Description (Now comes last)
            TextView descText = convertView.findViewById(R.id.regionDescriptionText);
            descText.setText(item.description);

            // Buttons
            TextView editBtn = convertView.findViewById(R.id.editButton);
            TextView deleteBtn = convertView.findViewById(R.id.deleteButton);

            editBtn.setOnClickListener(v -> {
                Intent intent = new Intent(RegionListActivity.this, RegionFormActivity.class);
                intent.putExtra("regionId", item.id);
                startActivity(intent);
            });

            deleteBtn.setOnClickListener(v -> confirmDelete(item.id));

            return convertView;
        }
    }

    private static class RegionItem {
        String id;
        String name;
        String orchardName;
        String description;
        int treeCount;
        String treeDetails;
    }

}