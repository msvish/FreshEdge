package com.example.smartfridgeapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    /**
     * Called when a message is received from Firebase (Pi/Laptop pipeline).
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: "FreshEdge Suggestion 🥗"
        val body = remoteMessage.notification?.body ?: "Open the app to see your recipe!"

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ── 1. Ensure Channel Exists (Required for Android 8.0+) ──
        val channel = NotificationChannel(
            "MEAL_ALERTS",
            "Meal Suggestions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for Phi-3 generated recipes"
        }
        notificationManager.createNotificationChannel(channel)

        // ── 2. Create the Intent to open MainActivity ──
        val intent = Intent(this, MainActivity::class.java).apply {
            // Clears the activity stack so the app opens fresh to the current recipe
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // ── 3. Wrap in PendingIntent for interactivity ──
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── 4. Build and Show the Notification ──
        val notification = NotificationCompat.Builder(this, "MEAL_ALERTS")
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent) // <── This makes it clickable
            .setAutoCancel(true)             // <── Removes notification after tap
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Use a unique ID (timestamp) so multiple suggestions don't overwrite each other
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Called when a new token is generated (e.g., first run or app reinstall).
     * Syncs to Firestore so your Raspberry Pi knows where to send alerts.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Refreshed token: $token")

        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "token"      to token,
            "updated_at" to com.google.firebase.Timestamp.now()
        )

        // Maps to the structure your Pi script expects [cite: 16, 66]
        db.collection("fcm_tokens")
            .document("user_1")
            .set(data)
            .addOnSuccessListener {
                Log.d("FCM_TOKEN", "✅ Token synced to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("FCM_TOKEN", "❌ Token sync failed: ${e.message}")
            }
    }
}