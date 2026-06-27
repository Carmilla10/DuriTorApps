package com.example.duritor;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

public class MainActivity extends DrawerActivity {

    private TextView alertText, timeText, regionText, orchardText, photoStatusText;
    private TextView orchardCountText, regionCountText, treeCountText;
    private ImageView capturedImageView;
    private DatabaseReference databaseReference;
    private FirebaseAuth mAuth;
    private String currentDisplayedEventId = "";
    private String currentDisplayedPhotoUrl = "";
    private static final String CHANNEL_ID = "fall_alert_channel";
    private String lastNotifiedEventId = "";
    private boolean initialFallEventsLoaded = false;
    // track downloads to avoid duplicate work
    private final Set<String> downloadingEventIds = Collections.synchronizedSet(new HashSet<>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupDrawer(R.id.nav_dashboard, getString(R.string.app_name));

        alertText = findViewById(R.id.alertText);
        timeText = findViewById(R.id.timeText);
        regionText = findViewById(R.id.regionText);
        orchardText = findViewById(R.id.orchardText);
        orchardCountText = findViewById(R.id.orchardCountText);
        regionCountText = findViewById(R.id.regionCountText);
        treeCountText = findViewById(R.id.treeCountText);
        photoStatusText = findViewById(R.id.photoStatusText);
        capturedImageView = findViewById(R.id.capturedImageView);
        capturedImageView.setOnClickListener(v -> {
            if (currentDisplayedPhotoUrl != null && !currentDisplayedPhotoUrl.isEmpty()) {
                ImageViewer.show(MainActivity.this, currentDisplayedPhotoUrl);
            }
        });

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        createNotificationChannel();
        startBackgroundNotificationService();

        databaseReference = FirebaseDatabase.getInstance().getReference("fallEvents");
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChildren()) {
                    DataSnapshot latestEvent = findLatestEvent(snapshot);
                    if (latestEvent != null) {
                        if (!initialFallEventsLoaded) {
                            String initialEventId = latestEvent.getKey();
                            if (initialEventId != null) {
                                lastNotifiedEventId = initialEventId;
                            }
                        }
                        updateFallEvent(latestEvent, initialFallEventsLoaded);
                    }
                } else {
                    runOnUiThread(() -> {
                        alertText.setText("No fall detected");
                        timeText.setText("Waiting for fall...");
                        regionText.setText("...");
                        orchardText.setText("...");
                        photoStatusText.setText("No fall event image yet");
                        capturedImageView.setImageResource(android.R.drawable.ic_menu_camera);
                        currentDisplayedEventId = "";
                        currentDisplayedPhotoUrl = "";
                    });
                }
                initialFallEventsLoaded = true;
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e("MainActivity", "Database error: " + error.getMessage());
            }
        });

        loadCounts();

        findViewById(R.id.historyButton).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        findViewById(R.id.mapButton).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, MapActivity.class)));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Fall Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void sendLocalNotification(String title, String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w("MainActivity", "Notification skipped because POST_NOTIFICATIONS is not granted");
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setSubText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            showLogoutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadCounts() {
        FirebaseDatabase.getInstance().getReference("orchards").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                orchardCountText.setText(String.valueOf(snapshot.getChildrenCount()));
            }
            @Override
            public void onCancelled(DatabaseError error) {
                orchardCountText.setText("—");
            }
        });

        FirebaseDatabase.getInstance().getReference("regions").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                regionCountText.setText(String.valueOf(snapshot.getChildrenCount()));
            }
            @Override
            public void onCancelled(DatabaseError error) {
                regionCountText.setText("—");
            }
        });

        FirebaseDatabase.getInstance().getReference("trees").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                treeCountText.setText(String.valueOf(snapshot.getChildrenCount()));
            }
            @Override
            public void onCancelled(DatabaseError error) {
                treeCountText.setText("—");
            }
        });
    }

    private DataSnapshot findLatestEvent(DataSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }

        if (isFallEventSnapshot(snapshot)) {
            return snapshot;
        }

        DataSnapshot latest = null;
        String latestTimestampKey = null;

        for (DataSnapshot child : snapshot.getChildren()) {
            if (!isFallEventSnapshot(child)) continue;

            String date = child.child("date").getValue(String.class);
            String time = child.child("time").getValue(String.class);
            if (date == null) date = "";
            if (time == null) time = "";

            String sortKey = date + " " + time;
            if (latest == null || latestTimestampKey == null || sortKey.compareTo(latestTimestampKey) > 0) {
                latest = child;
                latestTimestampKey = sortKey;
            }
        }

        if (latest != null && currentDisplayedEventId.equals(latest.getKey())) {
            Boolean isCollected = latest.child("collected").getValue(Boolean.class);
            if (isCollected != null && isCollected) {
                DataSnapshot nextLatest = null;
                String nextLatestTimestampKey = null;

                for (DataSnapshot child : snapshot.getChildren()) {
                    if (!isFallEventSnapshot(child)) continue;

                    Boolean collected = child.child("collected").getValue(Boolean.class);
                    if (collected == null || !collected) {
                        String date = child.child("date").getValue(String.class);
                        String time = child.child("time").getValue(String.class);
                        if (date == null) date = "";
                        if (time == null) time = "";

                        String sortKey = date + " " + time;
                        if (nextLatest == null || nextLatestTimestampKey == null || sortKey.compareTo(nextLatestTimestampKey) > 0) {
                            nextLatest = child;
                            nextLatestTimestampKey = sortKey;
                        }
                    }
                }

                return nextLatest != null ? nextLatest : latest;
            }
        }

        return latest;
    }

    private boolean isFallEventSnapshot(DataSnapshot snapshot) {
        if (snapshot == null) return false;
        return snapshot.hasChild("alert") || snapshot.hasChild("date") || snapshot.hasChild("time") || snapshot.hasChild("photoUrl") || snapshot.hasChild("imageUrl") || snapshot.hasChild("url");
    }

    private void updateFallEvent(DataSnapshot snapshot, boolean isInitialLoad) {
        String eventId = snapshot.getKey();
        currentDisplayedEventId = eventId;

        String alert = snapshot.child("alert").getValue(String.class);
        String date = snapshot.child("date").getValue(String.class);
        String time = snapshot.child("time").getValue(String.class);
        String photoUrl = readImageValue(snapshot);
        String orchardName = snapshot.child("orchardName").getValue(String.class);
        String regionName = snapshot.child("regionName").getValue(String.class);
        String treeName = snapshot.child("treeName").getValue(String.class);
        // Fallback: some devices (Arduino sketch) write `treeId` instead of `treeName`
        if ((treeName == null || treeName.isEmpty())) {
            treeName = snapshot.child("treeId").getValue(String.class);
        }

        if (alert == null || alert.isEmpty()) alert = "Durian Fall Detected!";
        if (date == null || date.isEmpty()) date = "Unknown Date";
        if (time == null || time.isEmpty()) time = "Unknown Time";
        if (orchardName == null || orchardName.isEmpty()) orchardName = "Unknown Orchard";
        if (regionName == null || regionName.isEmpty()) regionName = "Unknown Region";
        if (treeName == null || treeName.isEmpty()) treeName = "Unknown Tree";

        final String fullAlertMessage = alert;
        final String displayTime = time;
        final String displayRegion = regionName;
        final String displayOrchard = orchardName;
        final String finalPhotoUrl = photoUrl;
        final String finalEventId = eventId;
        final boolean isNewEvent = !isInitialLoad && !finalEventId.equals(lastNotifiedEventId);

        runOnUiThread(() -> {
            alertText.setText(fullAlertMessage);
            alertText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.alert_active));

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (alertText != null) {
                    alertText.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.text_primary));
                }
            }, 3000);

            timeText.setText("🕒 " + displayTime);
            regionText.setText("📍 " + displayRegion);
            orchardText.setText("🌳 " + displayOrchard);

            loadFallImage(finalEventId, finalPhotoUrl);

            if (isNewEvent) {
                String notificationTitle = "🚨 " + fullAlertMessage;
                String notificationMessage = "Orchard: " + displayOrchard + "\nRegion: " + displayRegion;
                Toast.makeText(MainActivity.this, fullAlertMessage, Toast.LENGTH_LONG).show();
                Log.d("MainActivity", "Notification details: " + notificationMessage);
                sendLocalNotification(notificationTitle, notificationMessage);
                lastNotifiedEventId = finalEventId;
            }
        });
    }

    private String readImageValue(DataSnapshot snapshot) {
        String[] possibleKeys = {
                "url",
                "photoUrl",
                "photoURL",
                "imageUrl",
                "imageURL",
                "imgUrl",
                "imgURL",
                "image",
                "downloadUrl",
                "storagePath",
                "photoPath"
        };

        for (String key : possibleKeys) {
            String value = snapshot.child(key).getValue(String.class);
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return null;
    }

    private void loadFallImage(String eventId, String imageValue) {
        if (imageValue == null || imageValue.trim().isEmpty() || "null".equalsIgnoreCase(imageValue.trim())) {
            Log.w("MainActivity", "No image value found for latest fall event");
            photoStatusText.setText("Firebase event found, but no photoUrl");
            capturedImageView.setImageResource(android.R.drawable.ic_menu_camera);
            currentDisplayedPhotoUrl = "";
            return;
        }

        String cleanImageValue = imageValue.trim();
        currentDisplayedPhotoUrl = cleanImageValue;
        photoStatusText.setText("Loading image: " + cleanImageValue);
        try {
            if (cleanImageValue.startsWith("http")) {
                // Try local cache first
                File cacheDir = new File(getCacheDir(), "fall_images");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File cachedFile = new File(cacheDir, eventId + ".jpg");

                if (cachedFile.exists() && cachedFile.length() > 0) {
                    loadImageUrl(cachedFile.toURI().toString(), capturedImageView);
                } else {
                    // Download to local cache in background; show network image as fallback
                    downloadToLocalCache(eventId, cleanImageValue, capturedImageView);
                    loadImageUrl(cleanImageValue, capturedImageView);
                }
            } else {
                Log.w("MainActivity", "Unsupported non-HTTP photoUrl: " + cleanImageValue);
                photoStatusText.setText("Unsupported image path: " + cleanImageValue);
                capturedImageView.setImageResource(android.R.drawable.ic_menu_camera);
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Exception loading image: " + cleanImageValue, e);
            photoStatusText.setText("Image load error: " + cleanImageValue);
            capturedImageView.setImageResource(android.R.drawable.ic_menu_camera);
        }
    }

    private String sanitizeImageUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        return trimmed.replace(" ", "%20");
    }

    private String appendCacheParam(String url) {
        String sanitized = sanitizeImageUrl(url);
        return sanitized + (sanitized.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
    }

    private void downloadToLocalCache(String eventId, String imageUrl, ImageView target) {
        if (eventId == null || eventId.isEmpty() || imageUrl == null || imageUrl.isEmpty()) return;
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) return;

        if (!downloadingEventIds.add(eventId)) {
            Log.d("MainActivity", "Download already in progress for " + eventId);
            return;
        }

        new Thread(() -> {
            File cacheDir = new File(getCacheDir(), "fall_images");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File outFile = new File(cacheDir, eventId + ".jpg");

            try {
                URL url = new URL(imageUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 400) {
                    Log.e("MainActivity", "HTTP response code " + responseCode + " for " + imageUrl);
                    return;
                }

                try (InputStream is = connection.getInputStream();
                     FileOutputStream fos = new FileOutputStream(outFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        bos.write(buffer, 0, len);
                    }
                    bos.flush();
                }

                if (outFile.exists() && outFile.length() > 0) {
                    runOnUiThread(() -> {
                        loadImageUrl(outFile.toURI().toString(), target);
                        photoStatusText.setText("Image loaded from cache");
                    });
                } else {
                    Log.e("MainActivity", "Downloaded file empty for " + imageUrl);
                }
            } catch (IOException e) {
                Log.e("MainActivity", "Failed to download image to local cache: " + imageUrl, e);
            } finally {
                downloadingEventIds.remove(eventId);
            }
        }).start();
    }

    private void loadImageUrl(String url, ImageView target) {
        String sanitizedUrl = sanitizeImageUrl(url);
        Log.d("MainActivity", "Loading image URL: " + sanitizedUrl);
        Glide.with(this)
                .load(Uri.parse(sanitizedUrl))
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .timeout(15000)
                        .skipMemoryCache(false)
                        .centerCrop())
                .placeholder(android.R.drawable.ic_menu_camera)
                .error(android.R.drawable.ic_menu_camera)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target1, boolean isFirstResource) {
                        Log.e("MainActivity", "Glide load failed for URL: " + url, e);
                        if (photoStatusText != null) {
                            photoStatusText.setText("Image load failed: " + e.getMessage());
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target1, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        Log.d("MainActivity", "Glide image loaded: " + url);
                        if (photoStatusText != null) {
                            photoStatusText.setText("Image loaded");
                        }
                        return false;
                    }
                })
                .into(target);
    }

    private void startBackgroundNotificationService() {
        Intent serviceIntent = new Intent(this, FirebaseBackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }
}
