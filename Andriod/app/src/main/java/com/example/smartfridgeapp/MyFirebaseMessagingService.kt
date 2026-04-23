package com.example.smartfridgeapp

import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: "Smart Fridge"
        val body = remoteMessage.notification?.body ?: "You have a new message"

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            "MEAL_ALERTS",
            "Meal Suggestions",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, "MEAL_ALERTS")
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d("FCM_TOKEN", "Refreshed token: $token")

        // ── Save token to Firestore so Pi scheduler can read it ──
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "token"      to token,
            "updated_at" to com.google.firebase.Timestamp.now()
        )

        db.collection("fcm_tokens")
            .document("user_1")
            .set(data)
            .addOnSuccessListener {
                Log.d("FCM_TOKEN", "✅ Token synced Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("FCM_TOKEN", "❌ Token sync failed: ${e.message}")
            }
    }
}