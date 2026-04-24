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

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title  = remoteMessage.notification?.title ?: "FreshEdge Suggestion 🥗"
        val body   = remoteMessage.notification?.body  ?: "Open the app to see your recipe!"
        val screen = remoteMessage.data["screen"]      ?: "current"  // ← read from Pi payload

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            "MEAL_ALERTS",
            "Meal Suggestions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for Phi-3 generated recipes"
        }
        notificationManager.createNotificationChannel(channel)

        // ── Deep link: pass screen so MainActivity opens the right tab ──
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("screen", screen)   // ← "current" → bell screen
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "MEAL_ALERTS")
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Refreshed token: $token")

        val db = FirebaseFirestore.getInstance()
        db.collection("fcm_tokens")
            .document("user_1")
            .set(hashMapOf(
                "token"      to token,
                "updated_at" to com.google.firebase.Timestamp.now()
            ))
            .addOnSuccessListener { Log.d("FCM_TOKEN", "✅ Token synced to Firestore") }
            .addOnFailureListener { e -> Log.e("FCM_TOKEN", "❌ Token sync failed: ${e.message}") }
    }
}