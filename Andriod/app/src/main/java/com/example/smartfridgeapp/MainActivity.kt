package com.example.smartfridgeapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.smartfridgeapp.ui.theme.SmartFridgeAppTheme
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("FCM_PERMISSION", "Permission granted: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        askNotificationPermission()

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                android.util.Log.d("FCM_TOKEN", "Token: ${task.result}")

                // Save to Firestore so Pi scheduler can read it
                FirebaseFirestore.getInstance()
                    .collection("fcm_tokens")
                    .document("user_1")
                    .set(mapOf(
                        "token"      to task.result,
                        "updated_at" to com.google.firebase.Timestamp.now()
                    ))
                    .addOnSuccessListener {
                        android.util.Log.d("FCM_TOKEN", "✅ Token saved to Firestore")
                    }
            }
        }

        setContent {
            SmartFridgeAppTheme {
                AppNavigation()
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "MEAL_ALERTS",
            "Meal Suggestions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for Phi-3 generated recipes"
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

// --- NAVIGATION ---

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var setupDone by remember { mutableStateOf(MealPreferences.isSetupDone(context)) }
    var showVerification by remember { mutableStateOf(false) }
    var currentIngredients by remember { mutableStateOf(listOf<String>()) }
    var currentImageUrl by remember { mutableStateOf("") }

    when {
        !setupDone -> SetupScreen(onSetupComplete = { setupDone = true })

        showVerification -> IngredientVerificationScreen(
            imageUrl = currentImageUrl,
            ingredients = currentIngredients,
            onConfirm = { confirmedIngredients ->
                // These confirmed ingredients will go to Phi-3 for recipe generation
                android.util.Log.d("INGREDIENTS", "Confirmed: $confirmedIngredients")
                showVerification = false
            },
            onBack = { showVerification = false }
        )

        else -> HomeScreen(
            onResetSetup = {
                MealPreferences.clearSetup(context)
                setupDone = false
            },
            onTestVerification = {
                // Sample data — will be replaced with real Pi data later
                currentImageUrl = ""
                currentIngredients = listOf(
                    "eggs", "spinach", "milk", "leftover rice",
                    "cheddar cheese", "butter", "garlic", "onion"
                )
                showVerification = true
            }
        )
    }
}

// --- SETUP SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current

    val breakfastState = rememberTimePickerState(8, 0)
    val lunchState = rememberTimePickerState(12, 0)
    val dinnerState = rememberTimePickerState(18, 0)

    var currentStep by remember { mutableStateOf(0) }

    val stepTitle = listOf("Breakfast Time", "Lunch Time", "Dinner Time")
    val stepEmoji = listOf("🌅", "☀️", "🌙")
    val stepState = listOf(breakfastState, lunchState, dinnerState)

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FreshEdge Setup",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Step ${currentStep + 1} of 3",
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "${stepEmoji[currentStep]} ${stepTitle[currentStep]}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            TimePicker(state = stepState[currentStep])

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    OutlinedButton(onClick = { currentStep-- }) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(onClick = {
                    if (currentStep < 2) {
                        currentStep++
                    } else {
                        val breakfast = formatTime(breakfastState.hour, breakfastState.minute)
                        val lunch = formatTime(lunchState.hour, lunchState.minute)
                        val dinner = formatTime(dinnerState.hour, dinnerState.minute)
                        MealPreferences.saveMealTimes(context, breakfast, lunch, dinner)
                        onSetupComplete()
                    }
                }) {
                    Text(if (currentStep < 2) "Next" else "Done ✓")
                }
            }
        }
    }
}

// --- HOME SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onResetSetup: () -> Unit,
    onTestVerification: () -> Unit
) {
    val context = LocalContext.current
    val (breakfast, lunch, dinner) = MealPreferences.getMealTimes(context)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("FreshEdge 🥗") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Your Meal Schedule",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "FreshEdge will scan your fridge 1 hour before each meal.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            MealTimeCard(emoji = "🌅", meal = "Breakfast", time = breakfast)
            MealTimeCard(emoji = "☀️", meal = "Lunch", time = lunch)
            MealTimeCard(emoji = "🌙", meal = "Dinner", time = dinner)

            Spacer(modifier = Modifier.weight(1f))

            // Test button — remove later when Pi is sending real data
            Button(
                onClick = onTestVerification,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🧪 Preview Ingredient Verification")
            }

            OutlinedButton(
                onClick = onResetSetup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit Meal Times")
            }
        }
    }
}

@Composable
fun MealTimeCard(emoji: String, meal: String, time: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = emoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = meal, fontWeight = FontWeight.Medium)
                    Text(
                        text = time,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- HELPER ---

fun formatTime(hour: Int, minute: Int): String {
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}