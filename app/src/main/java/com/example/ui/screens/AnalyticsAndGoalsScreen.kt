package com.example.ui.screens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.MealEntry
import com.example.data.model.UserProfile
import com.example.data.model.WeightLog
import com.example.ui.theme.AmberSecondaryDark
import com.example.ui.theme.ProteinColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsAndGoalsScreen(
    profile: UserProfile,
    accountEmail: String? = null,
    meals: List<MealEntry>,
    weightLogs: List<WeightLog>,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onUpdateProfileGoals: (
        displayName: String,
        age: Int,
        gender: String,
        heightCm: Double,
        currentWeightKg: Double,
        targetWeightKg: Double,
        activityLevel: String,
        goalType: String,
        dietaryPreference: String
    ) -> Unit,
    onLogWeight: (weightKg: Double, bodyFatPct: Double, note: String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current

    // Profile form states initialized from profile
    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var ageStr by remember(profile.age) { mutableStateOf(profile.age.toString()) }
    var gender by remember(profile.gender) { mutableStateOf(profile.gender) }
    var heightStr by remember(profile.heightCm) { mutableStateOf(profile.heightCm.roundToInt().toString()) }
    var currentWeightStr by remember(profile.currentWeightKg) { mutableStateOf(profile.currentWeightKg.toString()) }
    var targetWeightStr by remember(profile.targetWeightKg) { mutableStateOf(profile.targetWeightKg.toString()) }
    var activityLevel by remember(profile.activityLevel) { mutableStateOf(profile.activityLevel) }
    var goalType by remember(profile.goalType) { mutableStateOf(profile.goalType) }
    var dietaryPreference by remember(profile.dietaryPreference) { mutableStateOf(profile.dietaryPreference) }

    // Weight check-in form
    var checkInWeightStr by remember(profile.currentWeightKg) { mutableStateOf(profile.currentWeightKg.toString()) }
    var checkInBodyFatStr by remember { mutableStateOf("21.5") }
    var checkInNote by remember { mutableStateOf("Morning check-in") }

    // Smart Reminders state
    var mealRemindersEnabled by remember { mutableStateOf(true) }
    var hydrationRemindersEnabled by remember { mutableStateOf(true) }
    var reminderStatus by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sendSmartNutritionNotification(context, profile)
            reminderStatus = "Smart Nutrition & Hydration notification sent!"
        } else {
            reminderStatus = "Notification permission declined."
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .testTag("analytics_goals_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Analytics, Progress & Goals",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "7-Day Calorie Adherence, Weight Trajectory, Mifflin-St Jeor Calculator & Reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Weekly Calorie Adherence Chart Card
            item {
                WeeklyCalorieChartCard(
                    meals = meals,
                    calorieGoal = profile.dailyCalorieGoal
                )
            }

            // 2. Weight & Body Progress Chart + Check-In Logger
            item {
                WeightProgressCard(
                    profile = profile,
                    weightLogs = weightLogs,
                    checkInWeightStr = checkInWeightStr,
                    onWeightChange = { checkInWeightStr = it },
                    checkInBodyFatStr = checkInBodyFatStr,
                    onBodyFatChange = { checkInBodyFatStr = it },
                    checkInNote = checkInNote,
                    onNoteChange = { checkInNote = it },
                    onSaveCheckIn = {
                        val w = checkInWeightStr.toDoubleOrNull() ?: profile.currentWeightKg
                        val bf = checkInBodyFatStr.toDoubleOrNull() ?: 20.0
                        onLogWeight(w, bf, checkInNote)
                    }
                )
            }

            // 3. Personalized Mifflin-St Jeor Nutrition Goal Calculator
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("personalized_goals_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Personalized Nutrition Engine (Mifflin-St Jeor)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Active Targets: ${profile.dailyCalorieGoal} kcal • ${profile.dailyProteinGrams}g P • ${profile.dailyCarbsGrams}g C • ${profile.dailyFatGrams}g F • ${profile.dailyWaterMlGoal}ml Water",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f)
                            )
                            OutlinedTextField(
                                value = ageStr,
                                onValueChange = { ageStr = it },
                                label = { Text("Age") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = heightStr,
                                onValueChange = { heightStr = it },
                                label = { Text("Height (cm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = currentWeightStr,
                                onValueChange = { currentWeightStr = it },
                                label = { Text("Weight (kg)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = targetWeightStr,
                                onValueChange = { targetWeightStr = it },
                                label = { Text("Goal (kg)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text("Gender:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("female", "male", "other").forEach { g ->
                                FilterChip(
                                    selected = gender == g,
                                    onClick = { gender = g },
                                    label = { Text(g.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }

                        Text("Activity Level:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("sedentary", "light", "moderate", "active", "athlete").forEach { act ->
                                FilterChip(
                                    selected = activityLevel == act,
                                    onClick = { activityLevel = act },
                                    label = { Text(act.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }

                        Text("Primary Goal:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "lose" to "Lose Weight",
                                "recomp" to "Body Recomp",
                                "maintain" to "Maintain",
                                "gain" to "Lean Bulk"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = goalType == key,
                                    onClick = { goalType = key },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Text("Dietary Preference:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Balanced", "High Protein", "Mediterranean", "Plant-Based", "Low Carb", "Keto").forEach { style ->
                                FilterChip(
                                    selected = dietaryPreference == style,
                                    onClick = { dietaryPreference = style },
                                    label = { Text(style) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onUpdateProfileGoals(
                                    displayName.ifBlank { "Member" },
                                    ageStr.toIntOrNull() ?: 30,
                                    gender,
                                    heightStr.toDoubleOrNull() ?: 168.0,
                                    currentWeightStr.toDoubleOrNull() ?: 68.0,
                                    targetWeightStr.toDoubleOrNull() ?: 62.0,
                                    activityLevel,
                                    goalType,
                                    dietaryPreference
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("recalculate_goals_button")
                        ) {
                            Text("Recalculate & Save Personalized Goals")
                        }
                    }
                }
            }

            // 4. Smart Reminders, Dark Mode & Play Store Readiness Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Smart Reminders & App Preferences",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DarkMode, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Dark Theme Override")
                            }
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = onToggleDarkTheme,
                                modifier = Modifier.testTag("dark_theme_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Smart Meal Logging Reminders (8am, 1pm, 7pm)")
                            Switch(
                                checked = mealRemindersEnabled,
                                onCheckedChange = { mealRemindersEnabled = it }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Hydration Pacing Alerts (Every 2 hours)")
                            Switch(
                                checked = hydrationRemindersEnabled,
                                onCheckedChange = { hydrationRemindersEnabled = it }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    sendSmartNutritionNotification(context, profile)
                                    reminderStatus = "Dispatched Smart Nutrition & Hydration Notification!"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_smart_reminder_button")
                        ) {
                            Text("Send Smart Reminder Notification Now")
                        }

                        if (reminderStatus != null) {
                            Text(
                                text = reminderStatus!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 5. Privacy Policy, Security & Google Play Release Readiness Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Account, Privacy & Play Store Readiness",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Signed in as: ${profile.displayName}" +
                                (if (!accountEmail.isNullOrBlank()) " ($accountEmail)" else ""),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("signed_in_account_label")
                        )
                        Text(
                            text = "• Data Isolation: All user profiles, meals, hydration logs, weight records, and meal plans are stored under /users/${profile.userId} in Cloud Firestore with zero-trust ABAC security rules.\n" +
                                "• Zero Broad Storage Permissions: Uses Android Photo Picker (PickVisualMedia) for compliant photo selection.\n" +
                                "• AI Transparency: Food macro estimates include confidence scores and editable ingredient breakdowns.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out of Google Account")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyCalorieChartCard(
    meals: List<MealEntry>,
    calorieGoal: Int
) {
    val last7Days = remember(meals) {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFmt = SimpleDateFormat("EEE", Locale.US)
        (6 downTo 0).map { offset ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val key = fmt.format(cal.time)
            val dayLabel = labelFmt.format(cal.time)
            val totalKcal = meals.filter { it.dateKey == key }.sumOf { it.calories }
            Triple(key, dayLabel, totalKcal)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "7-Day Calorie Adherence",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Daily intake vs ${calorieGoal} kcal goal (Emerald = on target, Amber = above goal)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val maxVal = (maxOf(calorieGoal, last7Days.maxOfOrNull { it.third } ?: calorieGoal) * 1.15f).coerceAtLeast(1000f)
                val barCount = last7Days.size
                val slotWidth = size.width / barCount
                val barWidth = slotWidth * 0.48f

                // Goal dashed/solid line
                val goalY = size.height - ((calorieGoal / maxVal) * size.height)
                drawLine(
                    color = AmberSecondaryDark.copy(alpha = 0.6f),
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 2.dp.toPx()
                )

                last7Days.forEachIndexed { idx, (_, _, kcal) ->
                    val ratio = (kcal / maxVal).coerceIn(0.04f, 1f)
                    val barHeight = ratio * size.height
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val top = size.height - barHeight
                    val barColor = if (kcal > calorieGoal * 1.08) Color(0xFFF59E0B) else ProteinColor

                    drawRoundRect(
                        color = if (kcal == 0) Color.Gray.copy(alpha = 0.25f) else barColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                last7Days.forEach { (_, dayLabel, kcal) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (kcal > 0) "$kcal" else "—",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightProgressCard(
    profile: UserProfile,
    weightLogs: List<WeightLog>,
    checkInWeightStr: String,
    onWeightChange: (String) -> Unit,
    checkInBodyFatStr: String,
    onBodyFatChange: (String) -> Unit,
    checkInNote: String,
    onNoteChange: (String) -> Unit,
    onSaveCheckIn: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MonitorWeight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Weight & Body Composition Trajectory",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Current: ${profile.currentWeightKg} kg • Goal: ${profile.targetWeightKg} kg (${weightLogs.size} check-ins)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val points = remember(weightLogs, profile) {
                if (weightLogs.size >= 2) {
                    weightLogs.takeLast(10).map { it.weightKg.toFloat() }
                } else {
                    listOf(profile.currentWeightKg.toFloat(), profile.targetWeightKg.toFloat())
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val minW = (points.minOrNull() ?: 50f) - 2f
                val maxW = (points.maxOrNull() ?: 80f) + 2f
                val range = (maxW - minW).coerceAtLeast(1f)

                val path = Path()
                points.forEachIndexed { index, w ->
                    val x = if (points.size == 1) size.width / 2f else (index.toFloat() / (points.size - 1)) * size.width
                    val y = size.height - (((w - minW) / range) * size.height)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(
                        color = ProteinColor,
                        radius = 5.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
                drawPath(
                    path = path,
                    color = ProteinColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = checkInWeightStr,
                    onValueChange = onWeightChange,
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = checkInBodyFatStr,
                    onValueChange = onBodyFatChange,
                    label = { Text("Body Fat %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = checkInNote,
                onValueChange = onNoteChange,
                label = { Text("Progress Note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSaveCheckIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("log_weight_button")
            ) {
                Text("Log Weight Check-In")
            }
        }
    }
}

private fun sendSmartNutritionNotification(context: Context, profile: UserProfile) {
    val channelId = "nutripulse_smart_reminders"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "NutriPulse Smart Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Meal logging and hydration pacing reminders"
        }
        notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("NutriPulse Smart Check-In")
        .setContentText("Stay on pace for your ${profile.dailyCalorieGoal} kcal & ${profile.dailyProteinGrams}g protein goal! Log your meal or +250ml water.")
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(1001, notification)
}
