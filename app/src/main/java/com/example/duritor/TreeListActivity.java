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

public class TreeListActivity extends DrawerActivity {

    private EditText searchEditText;
    private FloatingActionButton addTreeButton;
    private ListView treeListView;
    private DatabaseReference orchardsRef;
    private DatabaseReference treesRef;
    private Map<String, String> orchardNameById;
    private boolean orchardsLoaded = false;
    private boolean treesLoaded = false;

    private List<TreeDisplayItem> masterTreeList;
    private List<TreeDisplayItem> filteredTreeList;
    private TreeAdapter treeAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDrawerShell(R.layout.activity_tree_list, R.id.nav_trees, R.string.title_trees);

        searchEditText = findViewById(R.id.searchEditText);
        addTreeButton = findViewById(R.id.addTreeButton);
        treeListView = findViewById(R.id.treeListView);

        orchardsRef = FirebaseDatabase.getInstance().getReference("orchards");
        treesRef = FirebaseDatabase.getInstance().getReference("trees");

        orchardNameById = new HashMap<>();
        masterTreeList = new ArrayList<>();
        filteredTreeList = new ArrayList<>();

        treeAdapter = new TreeAdapter();
        treeListView.setAdapter(treeAdapter);

        addTreeButton.setOnClickListener(v -> startActivity(new Intent(TreeListActivity.this, TreeFormActivity.class)));

        loadOrchards();
        loadTrees();

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTreeList(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadOrchards() {
        orchardsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                orchardNameById.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String orchardId = child.getKey();
                    if (orchardId == null) continue;
                    String name = child.child("name").getValue(String.class);
                    if (name == null || name.isEmpty()) {
                        name = "Unnamed Orchard";
                    }
                    orchardNameById.put(orchardId, name);
                }
                orchardsLoaded = true;
                refreshTreeOrchardNames();
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(TreeListActivity.this, "Failed to load orchards", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTrees() {
        treesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                masterTreeList.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String treeId = child.getKey();
                    if (treeId == null) continue;

                    String name = child.child("name").getValue(String.class);
                    String variety = child.child("durianVariety").getValue(String.class);
                    String orchardId = child.child("orchardId").getValue(String.class);
                    String orchardName = orchardNameById.getOrDefault(orchardId, "Unknown Orchard");
                    String regionName = child.child("regionName").getValue(String.class);
                    if (regionName == null || regionName.isEmpty()) {
                        regionName = "Unknown Region";
                    }

                    TreeDisplayItem item = new TreeDisplayItem();
                    item.treeId = treeId;
                    item.name = name != null ? name : "Unnamed Tree";
                    item.variety = variety != null ? variety : "Unknown Variety";
                    item.orchardId = orchardId;
                    item.orchardName = orchardName;
                    item.regionName = regionName;

                    masterTreeList.add(item);
                }
                treesLoaded = true;
                refreshTreeOrchardNames();
                filterTreeList(searchEditText.getText().toString());
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(TreeListActivity.this, "Failed to load trees", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterTreeList(String query) {
        filteredTreeList.clear();
        if (query.isEmpty()) {
            filteredTreeList.addAll(masterTreeList);
        } else {
            for (TreeDisplayItem item : masterTreeList) {
                if (item.name.toLowerCase().contains(query.toLowerCase()) ||
                        item.variety.toLowerCase().contains(query.toLowerCase())) {
                    filteredTreeList.add(item);
                }
            }
        }
        treeAdapter.notifyDataSetChanged();
    }

    private void confirmDelete(String treeId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Tree")
                .setMessage("Remove this tree from the system?")
                .setPositiveButton("Delete", (dialog, which) -> treesRef.child(treeId).removeValue())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private class TreeAdapter extends BaseAdapter {
        @Override
        public int getCount() { return filteredTreeList.size(); }
        @Override
        public Object getItem(int position) { return filteredTreeList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(TreeListActivity.this)
                        .inflate(R.layout.tree_item, parent, false);
            }

            TreeDisplayItem item = filteredTreeList.get(position);

            TextView nameText = convertView.findViewById(R.id.treeNameText);
            TextView detailsText = convertView.findViewById(R.id.treeDetailsText);
            TextView editBtn = convertView.findViewById(R.id.editButton);
            TextView deleteBtn = convertView.findViewById(R.id.deleteButton);

            nameText.setText(item.name + " - " + item.variety);
            detailsText.setText(item.orchardName + " • " + item.regionName);

            editBtn.setOnClickListener(v -> {
                Intent intent = new Intent(TreeListActivity.this, TreeFormActivity.class);
                intent.putExtra("treeId", item.treeId);
                startActivity(intent);
            });

            deleteBtn.setOnClickListener(v -> confirmDelete(item.treeId));

            return convertView;
        }
    }

    private static class TreeDisplayItem {
        String treeId;
        String name;
        String variety;
        String orchardId;
        String orchardName;
        String regionName;
    }

    private void refreshTreeOrchardNames() {
        if (!orchardsLoaded || !treesLoaded) {
            return;
        }
        boolean changed = false;
        for (TreeDisplayItem item : masterTreeList) {
            if (item.orchardId == null) continue;
            String resolvedName = orchardNameById.getOrDefault(item.orchardId, item.orchardName != null ? item.orchardName : "Unknown Orchard");
            if (!resolvedName.equals(item.orchardName)) {
                item.orchardName = resolvedName;
                changed = true;
            }
        }
        if (changed) {
            filterTreeList(searchEditText.getText().toString());
        }
    }

}