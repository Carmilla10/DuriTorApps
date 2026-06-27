package com.example.duritor;

import static androidx.core.content.ContextCompat.startActivity;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends DrawerActivity {

    private static final int LOCATION_REQUEST_CODE = 1001;
    private TextView mapLatitudeText;
    private TextView mapLongitudeText;
    private Button refreshLocationButton;
    private Button navigateButton;
    private Spinner mapOrchardSpinner;
    private DatabaseReference orchardsRef;
    private List<String> orchardIds;
    private List<String> orchardNames;
    private ArrayAdapter<String> orchardAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupDrawerShell(R.layout.activity_map, R.id.nav_map, R.string.title_map);

        mapLatitudeText = findViewById(R.id.mapLatitudeText);
        mapLongitudeText = findViewById(R.id.mapLongitudeText);
        refreshLocationButton = findViewById(R.id.refreshLocationButton);
        navigateButton = findViewById(R.id.navigateButton);
        mapOrchardSpinner = findViewById(R.id.mapOrchardSpinner);

        orchardsRef = FirebaseDatabase.getInstance().getReference("orchards");
        orchardIds = new ArrayList<>();
        orchardNames = new ArrayList<>();
        orchardAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, orchardNames);
        orchardAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapOrchardSpinner.setAdapter(orchardAdapter);

        refreshLocationButton.setOnClickListener(v -> refreshLocation());
        navigateButton.setOnClickListener(v -> openNavigation());

        loadOrchards();
        refreshLocation();
    }

    private void loadOrchards() {
        orchardsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                orchardIds.clear();
                orchardNames.clear();
                orchardIds.add("");
                orchardNames.add("Select orchard");
                for (DataSnapshot orchardSnapshot : snapshot.getChildren()) {
                    String id = orchardSnapshot.getKey();
                    String name = orchardSnapshot.child("name").getValue(String.class);
                    if (id != null) {
                        orchardIds.add(id);
                        orchardNames.add(name != null ? name : "Unnamed Orchard");
                    }
                }
                orchardAdapter.notifyDataSetChanged();

                // If there is only one orchard available, auto-select it so navigation can proceed quickly.
                if (orchardIds.size() == 2) {
                    mapOrchardSpinner.setSelection(1);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MapActivity.this, "Unable to load orchards", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
            return;
        }

        Location location = null;
        if (locationManager != null) {
            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        }

        if (location != null) {
            setLocationLabels(location.getLatitude(), location.getLongitude());
        } else {
            Toast.makeText(this, "Unable to determine current location. Please check GPS.", Toast.LENGTH_LONG).show();
        }
    }

    private void setLocationLabels(double latitude, double longitude) {
        // Show only the pure numbers
        mapLatitudeText.setText(String.valueOf(latitude));
        mapLongitudeText.setText(String.valueOf(longitude));
    }

    private void openNavigation() {
        int position = mapOrchardSpinner.getSelectedItemPosition();

        if (position <= 0 && orchardIds.size() == 2) {
            position = 1;
        }

        if (position <= 0 || position >= orchardIds.size()) {
            Toast.makeText(this, "Please select an orchard first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedId = orchardIds.get(position);
        orchardsRef.child(selectedId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String lat = getCoordinateValue(snapshot, "lat", "latitude");
                String lng = getCoordinateValue(snapshot, "lng", "lang", "longitude");
                String name = getValueAsString(snapshot.child("name"));

                if (lat == null || lat.isEmpty() || lng == null || lng.isEmpty()) {
                    String[] fallback = parseLocationField(snapshot.child("location").getValue(String.class));
                    if (fallback != null) {
                        lat = fallback[0];
                        lng = fallback[1];
                    }
                }

                if (lat != null && lng != null && !lat.isEmpty() && !lng.isEmpty()) {
                    String label = name != null && !name.isEmpty() ? name : "Orchard";
                    String mapsUri = "google.navigation:q=" + lat + "," + lng;
                    String geoUri = "geo:0,0?q=" + lat + "," + lng + "(" + Uri.encode(label) + ")";
                    String destination = lat + "," + lng;
                    startNavigation(mapsUri, geoUri, destination);
                } else {
                    Toast.makeText(MapActivity.this, "Orchard has no coordinates.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(MapActivity.this, "Unable to retrieve orchard location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String[] parseLocationField(String location) {
        if (location == null || location.isEmpty()) return null;
        String[] parts = location.split(",");
        if (parts.length >= 2) {
            String lat = parts[0].trim();
            String lng = parts[1].trim();
            if (!lat.isEmpty() && !lng.isEmpty()) {
                return new String[]{lat, lng};
            }
        }
        return null;
    }

    private String getValueAsString(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        return value != null ? value.toString() : null;
    }

    private String getCoordinateValue(DataSnapshot snapshot, String... keys) {
        for (String key : keys) {
            String value = getValueAsString(snapshot.child(key));
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void startNavigation(String mapsUriString, String geoUriString, String destination) {
        Intent geoIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUriString));
        try {
            startActivity(Intent.createChooser(geoIntent, "Open navigation"));
            return;
        } catch (ActivityNotFoundException ignored) {
            // continue to browser fallback
        }

        String webUri = "https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(webUri));
        try {
            startActivity(browserIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No map or browser application found.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            refreshLocation();
        }
    }
}