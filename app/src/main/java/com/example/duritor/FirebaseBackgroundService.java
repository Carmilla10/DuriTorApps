package com.example.duritor;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class FirebaseBackgroundService extends Service {

    private DatabaseReference databaseReference;
    private static final String CHANNEL_ID = "fall_service";
    private static final String DEFAULT_ORCHARD = "Orchard A";
    private static final String DEFAULT_REGION = "West Region";
    private String lastNotifiedEventId = "";
    private boolean initialEventsLoaded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // FIX: Create Notification object, not Builder
        Notification notification = getForegroundNotification();
        startForeground(1001, notification);

        // Listen for new fall events
        databaseReference = FirebaseDatabase.getInstance().getReference("fallEvents");

        databaseReference.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChildren()) {
                    DataSnapshot lastChild = null;
                    for (DataSnapshot child : snapshot.getChildren()) {
                        lastChild = child;
                    }
                    if (lastChild != null && lastChild.getKey() != null) {
                        lastNotifiedEventId = lastChild.getKey();
                    }
                }
                initialEventsLoaded = true;
                attachFallEventListener();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                initialEventsLoaded = true;
                attachFallEventListener();
            }
        });
    }

    private void attachFallEventListener() {
        databaseReference.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                tryNotifyFallEvent(snapshot);
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void tryNotifyFallEvent(DataSnapshot snapshot) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String eventId = snapshot.getKey();
        if (eventId == null || eventId.isEmpty()) {
            return;
        }

        if (!initialEventsLoaded) {
            return;
        }

        if (eventId.equals(lastNotifiedEventId)) {
            return;
        }

        String alert = snapshot.child("alert").getValue(String.class);
        String orchard = snapshot.child("orchardName").getValue(String.class);
        String region = snapshot.child("regionName").getValue(String.class);

        if (alert == null) alert = "Durian Fall Detected!";
        if (orchard == null || orchard.isEmpty()) orchard = DEFAULT_ORCHARD;
        if (region == null || region.isEmpty()) region = DEFAULT_REGION;

        lastNotifiedEventId = eventId;
        sendFallNotification(alert, orchard, region);
    }

    private void sendFallNotification(String alert, String orchard, String region) {
        String message = alert + " at " + orchard + " — " + region;

        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle("🚨 Fall Alert")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    // FIX: Return Notification object, not Builder
    private Notification getForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Duritor Monitoring")
                .setContentText("Listening for fall detections...")
                .setSmallIcon(R.drawable.logo)
                .setContentIntent(pendingIntent);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Fall Detection Service",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
