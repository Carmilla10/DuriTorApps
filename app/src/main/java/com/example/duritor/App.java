package com.example.duritor;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);

        // Get FCM token for the device
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    Log.d("FCM Device Token", token);
                    // You can send this token to your backend to store it
                })
                .addOnFailureListener(e -> {
                    Log.e("FCM", "Failed to get FCM token", e);
                });
    }
}
