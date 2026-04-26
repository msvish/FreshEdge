package com.example.smartfridgeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.google.firebase.firestore.FieldPath

// ── Current Recipe Screen ─────────────────────────────────────────
@Composable
fun CurrentRecipeScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var recipe by remember { mutableStateOf<Map<String, Any>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedMeal by remember { mutableStateOf<MealWindow?>(null) }

    val db = FirebaseFirestore.getInstance()

    // (Optional) keep this if you still need time-based logic elsewhere
    val (breakfast, lunch, dinner) = MealPreferences.getMealTimes(context)
    val currentMeal = getCurrentMeal(breakfast, lunch, dinner)

    LaunchedEffect(Unit) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date())

        val slots = listOf("Dinner", "Lunch", "Breakfast")

        fun checkSlot(index: Int) {
            if (index >= slots.size) {
                loading = false
                return
            }

            val slot = slots[index]

            db.collection("recipe_history")
                .document("user_1")
                .collection("days")
                .document(today)
                .collection(slot)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        recipe = snapshot.documents[0].data

                        selectedMeal = when (slot) {
                            "Breakfast" -> MealWindow("Breakfast", "🌅", "Morning meal")
                            "Lunch"     -> MealWindow("Lunch",     "☀️", "Afternoon meal")
                            "Dinner"    -> MealWindow("Dinner",    "🌙", "Evening meal")
                            else        -> null
                        }

                        android.util.Log.d("FIRESTORE_DEBUG", "Found in $slot")
                        loading = false
                    } else {
                        checkSlot(index + 1)
                    }
                }
                .addOnFailureListener {
                    android.util.Log.d("FIRESTORE_DEBUG", "Error in $slot", it)
                    checkSlot(index + 1)
                }
        }

        checkSlot(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (recipe != null) {

            val mealToShow = selectedMeal ?: currentMeal  // fallback safety

            // ✅ Meal banner (FIXED)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = mealToShow?.emoji ?: "🍽️",
                        fontSize = 36.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your latest meal: ${mealToShow?.name ?: ""}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = mealToShow?.timeLabel ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Today's suggestion",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = recipe!!["title"]?.toString() ?: "Recipe",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = recipe!!["instructions"]?.toString() ?: "",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val category = recipe!!["category"]?.toString()
                    val calories = recipe!!["calories"]?.toString()

                    if (!category.isNullOrEmpty() || !calories.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!category.isNullOrEmpty()) {
                                MetaBadge(
                                    text = category,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                )
                            }
                            if (!calories.isNullOrEmpty()) {
                                MetaBadge(
                                    text = "$calories cal",
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🥗", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No suggestion yet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "FreshEdge scans your fridge\n1 hour before each meal.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun MetaBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── History Screen ────────────────────────────────────────────────
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    var allMeals by remember { mutableStateOf(listOf<Map<String, Any>>()) }
    var loading by remember { mutableStateOf(true) }
    var openDate by remember { mutableStateOf<String?>(null) }
    var selectedMeal by remember { mutableStateOf<Map<String, Any>?>(null) }
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(Unit) {
        val result = mutableListOf<Map<String, Any>>()
        val slots = listOf("Breakfast", "Lunch", "Dinner")
        var completedSlots = 0

        slots.forEach { slotName ->
            // CollectionGroup finds ALL collections named "Lunch", etc. across all days
            db.collectionGroup(slotName)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    querySnapshot.forEach { doc ->
                        // Add the data and the Date (which is the ID of the parent's parent)
                        val dateStr = doc.reference.parent.parent?.id ?: "Unknown"
                        result.add(doc.data + mapOf("date" to dateStr))
                    }

                    completedSlots++
                    if (completedSlots == slots.size) {
                        allMeals = result.sortedWith(
                            compareByDescending<Map<String, Any>> { it["date"].toString() }
                                .thenBy {
                                    when (it["meal_type"]?.toString()) {
                                        "Breakfast" -> 0
                                        "Lunch"     -> 1
                                        else        -> 2
                                    }
                                }
                        )
                        loading = false
                    }
                }
                .addOnFailureListener {
                    completedSlots++
                    if (completedSlots == slots.size) loading = false
                }
        }
    }

    // Group by date, preserving descending order
    val grouped = allMeals.groupBy { it["date"]?.toString() ?: "Unknown" }
        .entries.toList()

    Column(modifier = Modifier.fillMaxSize()) {

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                when {
                    selectedMeal != null -> selectedMeal = null
                    openDate != null    -> openDate = null
                    else                -> onBack()
                }
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Meal journal", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (allMeals.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("No history yet", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Suggestions appear here after FreshEdge runs its first scan.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return@Column
        }

        // Scrollable list
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            grouped.forEach { (date, mealsOnDate) ->
                val isOpen = openDate == date

                // ── Day band header ──────────────────────────────
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openDate = if (isOpen) null else date
                                selectedMeal = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = formatDateHeader(date),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${mealsOnDate.size} meal${if (mealsOnDate.size != 1) "s" else ""}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isOpen)
                                    Icons.Default.KeyboardArrowUp
                                else
                                    Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // ── Meal rows (shown when band is open) ──────────
                if (isOpen) {
                    val orderedMeals = mealsOnDate.sortedBy {
                        when (it["meal_type"]?.toString()) {
                            "Breakfast" -> 0
                            "Lunch"     -> 1
                            "Dinner"    -> 2
                            else        -> 3
                        }
                    }

                    items(orderedMeals) { meal ->
                        val isSelected = selectedMeal == meal
                        val slot = meal["meal_type"]?.toString() ?: "Meal"
                        val dotColor = when (slot) {
                            "Breakfast" -> Color(0xFFEF9F27)
                            "Lunch"     -> Color(0xFF1D9E75)
                            else        -> Color(0xFF534AB7)
                        }

                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.secondaryContainer
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                    .clickable {
                                        selectedMeal = if (isSelected) null else meal
                                    }
                                    .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Coloured dot
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = slot.uppercase(),
                                        fontSize = 11.sp,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = meal["title"]?.toString() ?: "Recipe",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = ">",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // ── Recipe detail panel (inline expand) ──
                            if (isSelected) {
                                val badgeColor = when (slot) {
                                    "Breakfast" -> MaterialTheme.colorScheme.tertiaryContainer
                                    "Lunch"     -> MaterialTheme.colorScheme.secondaryContainer
                                    else        -> MaterialTheme.colorScheme.primaryContainer
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            start = 24.dp, end = 16.dp,
                                            top = 4.dp, bottom = 16.dp
                                        )
                                    ) {
                                        // Slot badge
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = badgeColor
                                        ) {
                                            Text(
                                                text = slot,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = meal["title"]?.toString() ?: "Recipe",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 22.sp
                                        )
                                        Divider(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        Text(
                                            text = meal["instructions"]?.toString() ?: "",
                                            fontSize = 14.sp,
                                            lineHeight = 21.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Meta pills
                                        val category = meal["category"]?.toString()
                                        val calories = meal["calories"]?.toString()
                                        if (!category.isNullOrEmpty() || !calories.isNullOrEmpty()) {
                                            Spacer(Modifier.height(12.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                if (!category.isNullOrEmpty()) MetaBadge(category, MaterialTheme.colorScheme.surfaceVariant)
                                                if (!calories.isNullOrEmpty()) MetaBadge("$calories cal", MaterialTheme.colorScheme.surfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealSlotTimeline(slot: String, meals: List<Map<String, Any>>) {
    val emoji = when (slot) {
        "Breakfast" -> "🌅"
        "Lunch"     -> "☀️"
        "Dinner"    -> "🌙"
        else        -> "🍽️"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // Timeline line + dot
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 16.sp)
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (meals.size > 1) (meals.size * 72).dp else 0.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Cards for each recipe in this slot
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = slot,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            meals.forEach { meal ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = meal["title"]?.toString() ?: "Recipe",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val suggestion = meal["instructions"]?.toString()
                        if (!suggestion.isNullOrEmpty()) {
                            Text(
                                text = suggestion,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────
data class MealWindow(val name: String, val emoji: String, val timeLabel: String)

fun getCurrentMeal(breakfast: String, lunch: String, dinner: String): MealWindow {
    val now = Calendar.getInstance()
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val min  = now.get(Calendar.MINUTE)
    val nowMins = hour * 60 + min

    fun toMins(t: String): Int {
        val parts = t.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    return when {
        nowMins < toMins(lunch)   -> MealWindow("Breakfast", "🌅", "Scheduled at $breakfast")
        nowMins < toMins(dinner)  -> MealWindow("Lunch",     "☀️", "Scheduled at $lunch")
        else                      -> MealWindow("Dinner",    "🌙", "Scheduled at $dinner")
    }
}

fun formatDateHeader(date: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val parsed = sdf.parse(date) ?: return date
        val today = Calendar.getInstance()
        val cal   = Calendar.getInstance().apply { time = parsed }
        when {
            isSameDay(cal, today)                        -> "Today"
            isSameDay(cal, today.apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
            else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(parsed)
        }
    } catch (e: Exception) { date }
}

fun isSameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)