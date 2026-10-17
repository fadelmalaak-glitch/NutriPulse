package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyNutritionSummary
import com.example.data.model.MealEntry
import com.example.data.model.NutritionCalculator
import com.example.data.model.WaterLog
import com.example.ui.theme.CarbsColor
import com.example.ui.theme.FatColor
import com.example.ui.theme.ProteinColor
import com.example.ui.theme.WaterColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    selectedDateKey: String,
    onSelectDateKey: (String) -> Unit,
    summary: DailyNutritionSummary,
    meals: List<MealEntry>,
    waterLogs: List<WaterLog>,
    onOpenAiLogger: (initialTab: Int) -> Unit,
    onScaleMealPortion: (MealEntry, Double) -> Unit,
    onUpdateMeal: (MealEntry) -> Unit,
    onDeleteMeal: (String) -> Unit,
    onLogWater: (Int, String) -> Unit,
    onDeleteWater: (String) -> Unit
) {
    var editingMeal by remember { mutableStateOf<MealEntry?>(null) }
    var selectedBeverage by remember { mutableStateOf("water") }

    val dayMeals = remember(meals, selectedDateKey) {
        meals.filter { it.dateKey == selectedDateKey }
    }
    val dayWaterLogs = remember(waterLogs, selectedDateKey) {
        waterLogs.filter { it.dateKey == selectedDateKey }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .testTag("dashboard_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Date Navigator Bar
            item {
                DateNavigatorBar(
                    selectedDateKey = selectedDateKey,
                    onSelectDateKey = onSelectDateKey
                )
            }

            // 2. Hero Calorie & Macro Multi-Ring Dashboard Card
            item {
                CalorieMacroHeroCard(summary = summary)
            }

            // 3. Multimodal Quick AI Logging Bar (Photo, Voice, Chat, Barcode, Manual)
            item {
                QuickLogMethodsRow(onOpenAiLogger = onOpenAiLogger)
            }

            // 4. Micronutrients & Accuracy Bar
            item {
                MicronutrientStripCard(summary = summary)
            }

            // 5. Daily Food Diary Sections (Breakfast, Lunch, Dinner, Snack)
            val mealSections = listOf(
                "breakfast" to "Breakfast",
                "lunch" to "Lunch",
                "dinner" to "Dinner",
                "snack" to "Snacks & Drinks"
            )

            items(mealSections, key = { it.first }) { (typeKey, sectionTitle) ->
                val sectionMeals = dayMeals.filter { it.mealType.equals(typeKey, ignoreCase = true) }
                MealSectionCard(
                    mealType = typeKey,
                    title = sectionTitle,
                    meals = sectionMeals,
                    onAddClick = { onOpenAiLogger(1) },
                    onScalePortion = onScaleMealPortion,
                    onEditClick = { editingMeal = it },
                    onDeleteClick = onDeleteMeal
                )
            }

            // 6. Hydration Tracker Card
            item {
                HydrationTrackerCard(
                    summary = summary,
                    selectedBeverage = selectedBeverage,
                    onSelectBeverage = { selectedBeverage = it },
                    waterLogs = dayWaterLogs,
                    onLogWater = onLogWater,
                    onDeleteWater = onDeleteWater
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (editingMeal != null) {
        EditMealDialog(
            meal = editingMeal!!,
            onDismiss = { editingMeal = null },
            onConfirm = { updated ->
                onUpdateMeal(updated)
                editingMeal = null
            }
        )
    }
}

@Composable
private fun DateNavigatorBar(
    selectedDateKey: String,
    onSelectDateKey: (String) -> Unit
) {
    val todayKey = remember { NutritionCalculator.todayDateKey() }

    fun shiftDate(dateStr: String, deltaDays: Int): String {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = fmt.parse(dateStr) ?: return todayKey
            val cal = Calendar.getInstance().apply {
                time = parsed
                add(Calendar.DAY_OF_YEAR, deltaDays)
            }
            fmt.format(cal.time)
        } catch (e: Exception) {
            todayKey
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { onSelectDateKey(shiftDate(selectedDateKey, -1)) },
                modifier = Modifier.testTag("prev_day_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Day"
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelectDateKey(todayKey) }
            ) {
                Text(
                    text = if (selectedDateKey == todayKey) "Today • $selectedDateKey" else selectedDateKey,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (selectedDateKey != todayKey) {
                    Text(
                        text = "Tap to return to Today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = { onSelectDateKey(shiftDate(selectedDateKey, 1)) },
                modifier = Modifier.testTag("next_day_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Day"
                )
            }
        }
    }
}

@Composable
private fun CalorieMacroHeroCard(summary: DailyNutritionSummary) {
    val calProgress by animateFloatAsState(
        targetValue = (summary.totalCalories.toFloat() / summary.calorieGoal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1.25f),
        animationSpec = spring(),
        label = "calProgress"
    )
    val proteinProgress by animateFloatAsState(
        targetValue = (summary.totalProteinGrams.toFloat() / summary.proteinGoalGrams.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "proteinProgress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calorie_hero_card"),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF062C24),
                            Color(0xFF0D4A3E),
                            Color(0xFF115E59)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "AI Confidence",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI Accuracy ${summary.averageConfidence}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val isOver = summary.remainingCalories < 0
                        Text(
                            text = "${abs(summary.remainingCalories)}",
                            style = MaterialTheme.typography.displayLarge,
                            color = if (isOver) Color(0xFFFCA5A5) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isOver) "kcal over daily target" else "kcal remaining today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Eaten: ${summary.totalCalories} / ${summary.calorieGoal} kcal",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFDE68A)
                        )
                    }

                    // Concentric Calorie & Protein Ring Canvas
                    Box(
                        modifier = Modifier.size(132.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeOuter = 13.dp.toPx()
                            val strokeInner = 9.dp.toPx()
                            val diameterOuter = size.minDimension - strokeOuter
                            val topLeftOuter = Offset(
                                (size.width - diameterOuter) / 2f,
                                (size.height - diameterOuter) / 2f
                            )

                            // Background Track Outer
                            drawArc(
                                color = Color.White.copy(alpha = 0.14f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeftOuter,
                                size = Size(diameterOuter, diameterOuter),
                                style = Stroke(width = strokeOuter, cap = StrokeCap.Round)
                            )

                            // Active Calorie Arc
                            drawArc(
                                color = if (summary.remainingCalories < 0) Color(0xFFF87171) else Color(0xFF34D399),
                                startAngle = -90f,
                                sweepAngle = (calProgress.coerceAtMost(1f) * 360f),
                                useCenter = false,
                                topLeft = topLeftOuter,
                                size = Size(diameterOuter, diameterOuter),
                                style = Stroke(width = strokeOuter, cap = StrokeCap.Round)
                            )

                            val inset = strokeOuter + 8.dp.toPx()
                            val diameterInner = size.minDimension - inset * 2
                            val topLeftInner = Offset(
                                (size.width - diameterInner) / 2f,
                                (size.height - diameterInner) / 2f
                            )

                            // Background Track Inner (Protein)
                            drawArc(
                                color = Color.White.copy(alpha = 0.12f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeftInner,
                                size = Size(diameterInner, diameterInner),
                                style = Stroke(width = strokeInner, cap = StrokeCap.Round)
                            )

                            // Active Protein Arc
                            drawArc(
                                color = Color(0xFFFBBF24),
                                startAngle = -90f,
                                sweepAngle = (proteinProgress * 360f),
                                useCenter = false,
                                topLeft = topLeftInner,
                                size = Size(diameterInner, diameterInner),
                                style = Stroke(width = strokeInner, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val pct = ((summary.totalCalories.toFloat() / summary.calorieGoal.coerceAtLeast(1)) * 100).roundToInt()
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "of goal",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3-Column Macro Progress Bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MacroGaugeColumn(
                        label = "Protein",
                        currentGrams = summary.totalProteinGrams,
                        goalGrams = summary.proteinGoalGrams,
                        color = ProteinColor,
                        modifier = Modifier.weight(1f)
                    )
                    MacroGaugeColumn(
                        label = "Carbs",
                        currentGrams = summary.totalCarbsGrams,
                        goalGrams = summary.carbsGoalGrams,
                        color = CarbsColor,
                        modifier = Modifier.weight(1f)
                    )
                    MacroGaugeColumn(
                        label = "Fat",
                        currentGrams = summary.totalFatGrams,
                        goalGrams = summary.fatGoalGrams,
                        color = FatColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroGaugeColumn(
    label: String,
    currentGrams: Double,
    goalGrams: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = (currentGrams.toFloat() / goalGrams.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    Surface(
        color = Color.White.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${currentGrams.roundToInt()}g / ${goalGrams}g",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = color,
                trackColor = Color.White.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun QuickLogMethodsRow(
    onOpenAiLogger: (initialTab: Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickLogMethodButton(
            icon = Icons.Default.CameraAlt,
            label = "Photo AI",
            onClick = { onOpenAiLogger(0) },
            modifier = Modifier
                .weight(1f)
                .testTag("quick_log_photo")
        )
        QuickLogMethodButton(
            icon = Icons.Default.Mic,
            label = "Voice / Chat",
            onClick = { onOpenAiLogger(1) },
            modifier = Modifier
                .weight(1f)
                .testTag("quick_log_voice")
        )
        QuickLogMethodButton(
            icon = Icons.Default.QrCodeScanner,
            label = "Barcode",
            onClick = { onOpenAiLogger(2) },
            modifier = Modifier
                .weight(1f)
                .testTag("quick_log_barcode")
        )
        QuickLogMethodButton(
            icon = Icons.Default.Edit,
            label = "Manual",
            onClick = { onOpenAiLogger(3) },
            modifier = Modifier
                .weight(1f)
                .testTag("quick_log_manual")
        )
    }
}

@Composable
private fun QuickLogMethodButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MicronutrientStripCard(summary: DailyNutritionSummary) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MicronutrientStat("Dietary Fiber", "${summary.totalFiberGrams.roundToInt()} / ${summary.fiberGoalGrams}g")
            MicronutrientStat("Sugars", "${summary.totalSugarGrams.roundToInt()}g")
            MicronutrientStat("Sodium", "${summary.totalSodiumMg}mg")
            MicronutrientStat("Hydration", "${summary.totalWaterMl}ml")
        }
    }
}

@Composable
internal fun MicronutrientStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealSectionCard(
    mealType: String,
    title: String,
    meals: List<MealEntry>,
    onAddClick: () -> Unit,
    onScalePortion: (MealEntry, Double) -> Unit,
    onEditClick: (MealEntry) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    val sectionCalories = meals.sumOf { it.calories }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("meal_section_$mealType"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$sectionCalories kcal • ${meals.size} item(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = onAddClick,
                    label = { Text("Log Food") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Log Food to $title",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("add_meal_$mealType")
                )
            }

            if (meals.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No items logged yet. Tap Photo AI, Voice/Chat, or Log Food to record your $title.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    meals.forEach { meal ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = meal.foodName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${meal.portionDescription} (${meal.portionMultiplier}x) • via ${meal.logMethod.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "${meal.calories} kcal",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Macro & Confidence Badges
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    MacroBadge("P: ${meal.proteinGrams.roundToInt()}g", ProteinColor)
                                    MacroBadge("C: ${meal.carbsGrams.roundToInt()}g", CarbsColor)
                                    MacroBadge("F: ${meal.fatGrams.roundToInt()}g", FatColor)
                                    MacroBadge("Fiber: ${meal.fiberGrams.roundToInt()}g", MaterialTheme.colorScheme.primary)
                                    MacroBadge("AI ${meal.confidenceScore}%", MaterialTheme.colorScheme.secondary)
                                }

                                if (meal.ingredientsSummary.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Ingredients: ${meal.ingredientsSummary}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (meal.aiNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = meal.aiNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Instant Portion Adjustment Pills & Edit/Delete Controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(0.5, 1.0, 1.5, 2.0).forEach { mult ->
                                            val isSelected = abs(meal.portionMultiplier - mult) < 0.05
                                            Surface(
                                                onClick = { onScalePortion(meal, mult) },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                            ) {
                                                Text(
                                                    text = "${mult}x",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = { onEditClick(meal) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Meal & Ingredients",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (meal.id.isNotBlank()) {
                                            IconButton(
                                                onClick = { onDeleteClick(meal.id) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete Meal",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HydrationTrackerCard(
    summary: DailyNutritionSummary,
    selectedBeverage: String,
    onSelectBeverage: (String) -> Unit,
    waterLogs: List<WaterLog>,
    onLogWater: (Int, String) -> Unit,
    onDeleteWater: (String) -> Unit
) {
    val progress = (summary.totalWaterMl.toFloat() / summary.waterGoalMl.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hydration_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(WaterColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalDrink,
                            contentDescription = "Water Intake",
                            tint = WaterColor
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Hydration Tracker",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${summary.totalWaterMl} / ${summary.waterGoalMl} ml (${(progress * 100).roundToInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = WaterColor,
                trackColor = WaterColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("water", "sparkling", "tea", "electrolytes", "coffee").forEach { bev ->
                    FilterChip(
                        selected = selectedBeverage == bev,
                        onClick = { onSelectBeverage(bev) },
                        label = { Text(bev.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onLogWater(250, selectedBeverage) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("log_water_250")
                ) {
                    Text("+250 ml")
                }
                OutlinedButton(
                    onClick = { onLogWater(500, selectedBeverage) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("log_water_500")
                ) {
                    Text("+500 ml")
                }
                Button(
                    onClick = { onLogWater(350, selectedBeverage) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("log_water_350")
                ) {
                    Text("+350 ml")
                }
            }

            if (waterLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                waterLogs.take(3).forEach { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "• ${log.amountMl}ml ${log.beverageType.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (log.id.isNotBlank()) {
                            TextButton(onClick = { onDeleteWater(log.id) }) {
                                Text("Undo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditMealDialog(
    meal: MealEntry,
    onDismiss: () -> Unit,
    onConfirm: (MealEntry) -> Unit
) {
    var foodName by remember { mutableStateOf(meal.foodName) }
    var portionDesc by remember { mutableStateOf(meal.portionDescription) }
    var caloriesStr by remember { mutableStateOf(meal.calories.toString()) }
    var proteinStr by remember { mutableStateOf(meal.proteinGrams.toString()) }
    var carbsStr by remember { mutableStateOf(meal.carbsGrams.toString()) }
    var fatStr by remember { mutableStateOf(meal.fatGrams.toString()) }
    var ingredients by remember { mutableStateOf(meal.ingredientsSummary) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Portion & Ingredients") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = foodName,
                    onValueChange = { foodName = it },
                    label = { Text("Food Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portionDesc,
                    onValueChange = { portionDesc = it },
                    label = { Text("Portion Size") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = caloriesStr,
                        onValueChange = { caloriesStr = it },
                        label = { Text("Calories") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = proteinStr,
                        onValueChange = { proteinStr = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carbsStr,
                        onValueChange = { carbsStr = it },
                        label = { Text("Carbs (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fatStr,
                        onValueChange = { fatStr = it },
                        label = { Text("Fat (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = ingredients,
                    onValueChange = { ingredients = it },
                    label = { Text("Ingredients Breakdown / Corrections") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        meal.copy(
                            foodName = foodName.ifBlank { meal.foodName },
                            portionDescription = portionDesc.ifBlank { meal.portionDescription },
                            calories = caloriesStr.toIntOrNull() ?: meal.calories,
                            proteinGrams = proteinStr.toDoubleOrNull() ?: meal.proteinGrams,
                            carbsGrams = carbsStr.toDoubleOrNull() ?: meal.carbsGrams,
                            fatGrams = fatStr.toDoubleOrNull() ?: meal.fatGrams,
                            ingredientsSummary = ingredients
                        )
                    )
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
