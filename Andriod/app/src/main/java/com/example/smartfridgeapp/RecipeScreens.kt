package com.example.smartfridgeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@Composable
fun CurrentRecipeScreen(onBack: () -> Unit) {
    var recipe by remember { mutableStateOf<Map<String, Any>?>(null) }
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) {
        // Fetch the latest confirmed recipe
        db.collection("recipe_history")
            .document("user_1")
            .collection("my_meals")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { if (!it.isEmpty) recipe = it.documents[0].data }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Today's Suggestion 🔔", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        if (recipe != null) {
            Text(recipe!!["title"].toString(), fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            Text(recipe!!["suggestion"].toString(), modifier = Modifier.padding(top = 8.dp))
        } else {
            Text("No active meal plan. FreshEdge scans 1 hour before your set times.")
        }
        Button(onClick = onBack, modifier = Modifier.padding(top = 20.dp)) { Text("Close") }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    // 1. Define the state at the TOP so the whole function can see it
    var historyList by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    val db = FirebaseFirestore.getInstance()

    // 2. Fetch the data
    LaunchedEffect(Unit) {
        db.collection("recipe_history")
            .document("user_1")
            .collection("my_meals")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                // Update the state defined above
                historyList = result.documents.mapNotNull { d -> d.data }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FIRESTORE", "Error: ${e.message}")
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Meal Journal 📜", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))

        // 3. historyList is now correctly referenced here
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(historyList) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = item["date"]?.toString() ?: "No Date",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = item["title"]?.toString() ?: "Untitled Recipe",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Close History")
        }
    }
}