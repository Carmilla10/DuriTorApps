package com.example.duritor;

import static androidx.core.content.ContextCompat.startActivity;

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
import java.util.List;

public class OrchardListActivity extends DrawerActivity {

    private ListView orchardListView;
    private EditText searchEditText;
    private FloatingActionButton addOrchardButton;

    private DatabaseReference orchardRef;
    private List<OrchardItem> masterOrchardList;
    private List<OrchardItem> filteredOrchardList;
    private OrchardAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDrawerShell(R.layout.activity_orchard_list, R.id.nav_orchards, R.string.title_orchards);

        // Initialize Views
        orchardListView = findViewById(R.id.orchardListView);
        searchEditText = findViewById(R.id.searchEditText);
        addOrchardButton = findViewById(R.id.addOrchardButton);

        // Initialize Lists and Adapter
        masterOrchardList = new ArrayList<>();
        filteredOrchardList = new ArrayList<>();
        adapter = new OrchardAdapter();
        orchardListView.setAdapter(adapter);

        // Firebase Reference
        orchardRef = FirebaseDatabase.getInstance().getReference("orchards");

        // Load Data
        loadOrchards();

        // 1. Floating Action Button -> Go to Add Form
        addOrchardButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrchardListActivity.this, OrchardFormActivity.class);
            startActivity(intent);
        });

        // 2. Search Functionality
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

    private void loadOrchards() {
        orchardRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                masterOrchardList.clear();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String orchardId = child.getKey();
                    String name = child.child("name").getValue(String.class);
                    String lat = child.child("lat").getValue(String.class);
                    String lng = child.child("lng").getValue(String.class);
                    String description = child.child("description").getValue(String.class);

                    OrchardItem item = new OrchardItem();
                    item.id = orchardId;
                    item.name = name != null ? name : "Unnamed Orchard";
                    item.lat = lat != null ? lat : "0.000000";
                    item.lng = lng != null ? lng : "0.000000";
                    item.description = description != null ? description : "";

                    masterOrchardList.add(item);
                }

                // Reset filtered list and update adapter
                filteredOrchardList.clear();
                filteredOrchardList.addAll(masterOrchardList);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(OrchardListActivity.this, "Failed to load orchards", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void filterList(String query) {
        filteredOrchardList.clear();
        if (query.isEmpty()) {
            filteredOrchardList.addAll(masterOrchardList);
        } else {
            for (OrchardItem item : masterOrchardList) {
                if (item.name.toLowerCase().contains(query.toLowerCase())) {
                    filteredOrchardList.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void confirmDelete(String orchardId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Orchard")
                .setMessage("Are you sure you want to delete this orchard?")
                .setPositiveButton("Delete", (dialog, which) -> deleteOrchard(orchardId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteOrchard(String orchardId) {
        orchardRef.child(orchardId).removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(OrchardListActivity.this, "Orchard deleted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(OrchardListActivity.this, "Delete failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- ADAPTER CLASS ---
    private class OrchardAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return filteredOrchardList.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredOrchardList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(OrchardListActivity.this)
                        .inflate(R.layout.orchard_item, parent, false);
            }

            OrchardItem item = filteredOrchardList.get(position);

            TextView nameText = convertView.findViewById(R.id.orchardNameText);
            TextView latText = convertView.findViewById(R.id.orchardLatText);
            TextView lngText = convertView.findViewById(R.id.orchardLngText);
            TextView descriptionText = convertView.findViewById(R.id.orchardDescriptionText);
            TextView editBtn = convertView.findViewById(R.id.editButton);
            TextView deleteBtn = convertView.findViewById(R.id.deleteButton);

            // Set Values
            nameText.setText(item.name);
            latText.setText("Latitude: " + item.lat);
            lngText.setText("Longitude: " + item.lng);
            descriptionText.setText(item.description);

            // Edit Button
            editBtn.setOnClickListener(v -> {
                Intent intent = new Intent(OrchardListActivity.this, OrchardFormActivity.class);
                intent.putExtra("orchardId", item.id);
                startActivity(intent);
            });

            // Delete Button
            deleteBtn.setOnClickListener(v -> confirmDelete(item.id));

            return convertView;
        }
    }

    // --- DATA MODEL CLASS ---
    private static class OrchardItem {
        String id;
        String name;
        String lat;
        String lng;
        String description;
    }
}