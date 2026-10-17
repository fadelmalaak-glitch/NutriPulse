package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Alex Rivera",
    val age: Int = 30,
    val gender: String = "female", // male, female, other
    val heightCm: Double = 168.0,
    val currentWeightKg: Double = 68.0,
    val targetWeightKg: Double = 62.0,
    val activityLevel: String = "moderate", // sedentary, light, moderate, active, athlete
    val goalType: String = "lose", // lose, maintain, gain, recomp
    val dietaryPreference: String = "Balanced", // Balanced, High Protein, Mediterranean, Plant-Based, Low Carb, Keto
    val dailyCalorieGoal: Int = 1850,
    val dailyProteinGrams: Int = 138,
    val dailyCarbsGrams: Int = 185,
    val dailyFatGrams: Int = 62,
    val dailyFiberGrams: Int = 28,
    val dailyWaterMlGoal: Int = 2500,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "displayName" to displayName.take(100).ifBlank { "Member" },
        "age" to age.coerceIn(13, 120),
        "gender" to gender,
        "heightCm" to heightCm.coerceIn(100.0, 250.0),
        "currentWeightKg" to currentWeightKg.coerceIn(25.0, 350.0),
        "targetWeightKg" to targetWeightKg.coerceIn(25.0, 350.0),
        "activityLevel" to activityLevel,
        "goalType" to goalType,
        "dietaryPreference" to dietaryPreference.take(60).ifBlank { "Balanced" },
        "dailyCalorieGoal" to dailyCalorieGoal.coerceIn(800, 10000),
        "dailyProteinGrams" to dailyProteinGrams.coerceIn(20, 600),
        "dailyCarbsGrams" to dailyCarbsGrams.coerceIn(10, 1200),
        "dailyFatGrams" to dailyFatGrams.coerceIn(15, 400),
        "dailyFiberGrams" to dailyFiberGrams.coerceIn(10, 100),
        "dailyWaterMlGoal" to dailyWaterMlGoal.coerceIn(500, 10000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "displayName" to displayName.take(100).ifBlank { "Member" },
        "age" to age.coerceIn(13, 120),
        "gender" to gender,
        "heightCm" to heightCm.coerceIn(100.0, 250.0),
        "currentWeightKg" to currentWeightKg.coerceIn(25.0, 350.0),
        "targetWeightKg" to targetWeightKg.coerceIn(25.0, 350.0),
        "activityLevel" to activityLevel,
        "goalType" to goalType,
        "dietaryPreference" to dietaryPreference.take(60).ifBlank { "Balanced" },
        "dailyCalorieGoal" to dailyCalorieGoal.coerceIn(800, 10000),
        "dailyProteinGrams" to dailyProteinGrams.coerceIn(20, 600),
        "dailyCarbsGrams" to dailyCarbsGrams.coerceIn(10, 1200),
        "dailyFatGrams" to dailyFatGrams.coerceIn(15, 400),
        "dailyFiberGrams" to dailyFiberGrams.coerceIn(10, 100),
        "dailyWaterMlGoal" to dailyWaterMlGoal.coerceIn(500, 10000),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class MealEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = NutritionCalculator.todayDateKey(),
    val mealType: String = "breakfast", // breakfast, lunch, dinner, snack
    val foodName: String = "",
    val portionDescription: String = "1 serving",
    val portionMultiplier: Double = 1.0,
    val calories: Int = 0,
    val proteinGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val fiberGrams: Double = 0.0,
    val sugarGrams: Double = 0.0,
    val sodiumMg: Int = 0,
    val confidenceScore: Int = 95,
    val logMethod: String = "manual", // photo, voice, chat, barcode, manual
    val ingredientsSummary: String = "",
    val aiNotes: String = "",
    val barcode: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "userId" to uid,
            "dateKey" to dateKey,
            "mealType" to mealType,
            "foodName" to foodName.take(150).ifBlank { "Logged Meal" },
            "portionDescription" to portionDescription.take(120).ifBlank { "1 serving" },
            "portionMultiplier" to portionMultiplier.coerceIn(0.1, 20.0),
            "calories" to calories.coerceIn(0, 15000),
            "proteinGrams" to proteinGrams.coerceIn(0.0, 1000.0),
            "carbsGrams" to carbsGrams.coerceIn(0.0, 2000.0),
            "fatGrams" to fatGrams.coerceIn(0.0, 1000.0),
            "fiberGrams" to fiberGrams.coerceIn(0.0, 300.0),
            "sugarGrams" to sugarGrams.coerceIn(0.0, 1000.0),
            "sodiumMg" to sodiumMg.coerceIn(0, 50000),
            "confidenceScore" to confidenceScore.coerceIn(0, 100),
            "logMethod" to logMethod,
            "ingredientsSummary" to ingredientsSummary.take(500),
            "aiNotes" to aiNotes.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!barcode.isNullOrBlank()) {
            base["barcode"] = barcode.take(64)
        }
        return base
    }

    fun toUpdateMap(): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "dateKey" to dateKey,
            "mealType" to mealType,
            "foodName" to foodName.take(150).ifBlank { "Logged Meal" },
            "portionDescription" to portionDescription.take(120).ifBlank { "1 serving" },
            "portionMultiplier" to portionMultiplier.coerceIn(0.1, 20.0),
            "calories" to calories.coerceIn(0, 15000),
            "proteinGrams" to proteinGrams.coerceIn(0.0, 1000.0),
            "carbsGrams" to carbsGrams.coerceIn(0.0, 2000.0),
            "fatGrams" to fatGrams.coerceIn(0.0, 1000.0),
            "fiberGrams" to fiberGrams.coerceIn(0.0, 300.0),
            "sugarGrams" to sugarGrams.coerceIn(0.0, 1000.0),
            "sodiumMg" to sodiumMg.coerceIn(0, 50000),
            "confidenceScore" to confidenceScore.coerceIn(0, 100),
            "logMethod" to logMethod,
            "ingredientsSummary" to ingredientsSummary.take(500),
            "aiNotes" to aiNotes.take(500),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!barcode.isNullOrBlank()) {
            base["barcode"] = barcode.take(64)
        }
        return base
    }
}

@IgnoreExtraProperties
data class WaterLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = NutritionCalculator.todayDateKey(),
    val amountMl: Int = 250,
    val beverageType: String = "water", // water, sparkling, tea, electrolytes, coffee
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "dateKey" to dateKey,
        "amountMl" to amountMl.coerceIn(1, 5000),
        "beverageType" to beverageType,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class WeightLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = NutritionCalculator.todayDateKey(),
    val weightKg: Double = 68.0,
    val bodyFatPct: Double = 22.0,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "dateKey" to dateKey,
        "weightKg" to weightKg.coerceIn(25.0, 350.0),
        "bodyFatPct" to bodyFatPct.coerceIn(0.0, 75.0),
        "note" to note.take(200),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class MealPlanItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dayOfWeek: String = "Today",
    val mealType: String = "lunch",
    val title: String = "",
    val calories: Int = 500,
    val proteinGrams: Int = 35,
    val carbsGrams: Int = 45,
    val fatGrams: Int = 18,
    val prepTimeMinutes: Int = 20,
    val ingredientsText: String = "",
    val instructionsText: String = "",
    val isCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "dayOfWeek" to dayOfWeek.take(20).ifBlank { "Today" },
        "mealType" to mealType,
        "title" to title.take(150).ifBlank { "Chef's Balanced Bowl" },
        "calories" to calories.coerceIn(0, 5000),
        "proteinGrams" to proteinGrams.coerceIn(0, 500),
        "carbsGrams" to carbsGrams.coerceIn(0, 1000),
        "fatGrams" to fatGrams.coerceIn(0, 500),
        "prepTimeMinutes" to prepTimeMinutes.coerceIn(0, 600),
        "ingredientsText" to ingredientsText.take(1000).ifBlank { "Fresh seasonal ingredients" },
        "instructionsText" to instructionsText.take(2000).ifBlank { "Prepare and serve warm." },
        "isCompleted" to isCompleted,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "dayOfWeek" to dayOfWeek.take(20).ifBlank { "Today" },
        "mealType" to mealType,
        "title" to title.take(150).ifBlank { "Chef's Balanced Bowl" },
        "calories" to calories.coerceIn(0, 5000),
        "proteinGrams" to proteinGrams.coerceIn(0, 500),
        "carbsGrams" to carbsGrams.coerceIn(0, 1000),
        "fatGrams" to fatGrams.coerceIn(0, 500),
        "prepTimeMinutes" to prepTimeMinutes.coerceIn(0, 600),
        "ingredientsText" to ingredientsText.take(1000).ifBlank { "Fresh seasonal ingredients" },
        "instructionsText" to instructionsText.take(2000).ifBlank { "Prepare and serve warm." },
        "isCompleted" to isCompleted,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class GroceryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val quantity: String = "1 unit",
    val category: String = "Produce",
    val checked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.take(120).ifBlank { "Item" },
        "quantity" to quantity.take(60).ifBlank { "1 unit" },
        "category" to category.take(40).ifBlank { "Produce" },
        "checked" to checked,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "name" to name.take(120).ifBlank { "Item" },
        "quantity" to quantity.take(60).ifBlank { "1 unit" },
        "category" to category.take(40).ifBlank { "Produce" },
        "checked" to checked,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class DailyNutritionSummary(
    val dateKey: String,
    val totalCalories: Int,
    val calorieGoal: Int,
    val remainingCalories: Int,
    val totalProteinGrams: Double,
    val proteinGoalGrams: Int,
    val totalCarbsGrams: Double,
    val carbsGoalGrams: Int,
    val totalFatGrams: Double,
    val fatGoalGrams: Int,
    val totalFiberGrams: Double,
    val fiberGoalGrams: Int,
    val totalSugarGrams: Double,
    val totalSodiumMg: Int,
    val totalWaterMl: Int,
    val waterGoalMl: Int,
    val averageConfidence: Int
)

object NutritionCalculator {
    fun todayDateKey(date: Date = Date()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return fmt.format(date)
    }

    /**
     * Calculates personalized daily calorie and macronutrient targets using the
     * Mifflin-St Jeor Equation and dietary preference macro ratios.
     */
    fun calculatePersonalizedGoals(
        age: Int,
        gender: String,
        heightCm: Double,
        currentWeightKg: Double,
        targetWeightKg: Double,
        activityLevel: String,
        goalType: String,
        dietaryPreference: String
    ): UserProfile {
        val safeAge = age.coerceIn(13, 120)
        val safeHeight = heightCm.coerceIn(100.0, 250.0)
        val safeWeight = currentWeightKg.coerceIn(25.0, 350.0)
        val safeTarget = targetWeightKg.coerceIn(25.0, 350.0)

        // Mifflin-St Jeor BMR
        val genderOffset = when (gender.lowercase(Locale.US)) {
            "male" -> 5.0
            "female" -> -161.0
            else -> -78.0
        }
        val bmr = (10.0 * safeWeight) + (6.25 * safeHeight) - (5.0 * safeAge) + genderOffset

        val activityMultiplier = when (activityLevel.lowercase(Locale.US)) {
            "sedentary" -> 1.2
            "light" -> 1.375
            "moderate" -> 1.55
            "active" -> 1.725
            "athlete" -> 1.9
            else -> 1.55
        }
        val tdee = bmr * activityMultiplier

        val goalDelta = when (goalType.lowercase(Locale.US)) {
            "lose" -> -500.0
            "gain" -> 350.0
            "recomp" -> -150.0
            else -> 0.0
        }

        val targetCalories = (tdee + goalDelta).roundToInt().coerceIn(1200, 6000)

        // Macro split (Protein / Carbs / Fat ratios)
        val (proteinPct, carbsPct, fatPct) = when (dietaryPreference.lowercase(Locale.US)) {
            "high protein" -> Triple(0.35, 0.35, 0.30)
            "low carb" -> Triple(0.35, 0.20, 0.45)
            "keto" -> Triple(0.25, 0.05, 0.70)
            "mediterranean" -> Triple(0.25, 0.45, 0.30)
            "plant-based" -> Triple(0.25, 0.50, 0.25)
            else -> if (goalType == "recomp") Triple(0.35, 0.38, 0.27) else Triple(0.30, 0.40, 0.30)
        }

        val proteinGrams = ((targetCalories * proteinPct) / 4.0).roundToInt().coerceIn(40, 400)
        val carbsGrams = ((targetCalories * carbsPct) / 4.0).roundToInt().coerceIn(20, 800)
        val fatGrams = ((targetCalories * fatPct) / 9.0).roundToInt().coerceIn(25, 300)
        val fiberGrams = ((targetCalories / 1000.0) * 14.0).roundToInt().coerceIn(20, 60)

        val activityWaterBonus = when (activityLevel.lowercase(Locale.US)) {
            "active" -> 400
            "athlete" -> 750
            "moderate" -> 250
            else -> 0
        }
        val waterGoalMl = ((safeWeight * 35.0).roundToInt() + activityWaterBonus).coerceIn(1500, 6000)

        return UserProfile(
            age = safeAge,
            gender = gender.lowercase(Locale.US),
            heightCm = safeHeight,
            currentWeightKg = safeWeight,
            targetWeightKg = safeTarget,
            activityLevel = activityLevel.lowercase(Locale.US),
            goalType = goalType.lowercase(Locale.US),
            dietaryPreference = dietaryPreference,
            dailyCalorieGoal = targetCalories,
            dailyProteinGrams = proteinGrams,
            dailyCarbsGrams = carbsGrams,
            dailyFatGrams = fatGrams,
            dailyFiberGrams = fiberGrams,
            dailyWaterMlGoal = waterGoalMl
        )
    }

    /**
     * Scales a MealEntry's macros proportionally when the user adjusts the portion multiplier.
     */
    fun scaleMealPortion(entry: MealEntry, newMultiplier: Double): MealEntry {
        val safeOld = if (entry.portionMultiplier <= 0.0) 1.0 else entry.portionMultiplier
        val safeNew = newMultiplier.coerceIn(0.25, 10.0)
        val ratio = safeNew / safeOld
        return entry.copy(
            portionMultiplier = (safeNew * 100.0).roundToInt() / 100.0,
            calories = (entry.calories * ratio).roundToInt().coerceIn(0, 15000),
            proteinGrams = ((entry.proteinGrams * ratio * 10.0).roundToInt() / 10.0).coerceIn(0.0, 1000.0),
            carbsGrams = ((entry.carbsGrams * ratio * 10.0).roundToInt() / 10.0).coerceIn(0.0, 2000.0),
            fatGrams = ((entry.fatGrams * ratio * 10.0).roundToInt() / 10.0).coerceIn(0.0, 1000.0),
            fiberGrams = ((entry.fiberGrams * ratio * 10.0).roundToInt() / 10.0).coerceIn(0.0, 300.0),
            sugarGrams = ((entry.sugarGrams * ratio * 10.0).roundToInt() / 10.0).coerceIn(0.0, 1000.0),
            sodiumMg = (entry.sodiumMg * ratio).roundToInt().coerceIn(0, 50000)
        )
    }

    /**
     * Computes the daily summary for a given dateKey from logged meals, water logs, and user profile.
     */
    fun computeDailySummary(
        dateKey: String,
        profile: UserProfile,
        meals: List<MealEntry>,
        waterLogs: List<WaterLog>
    ): DailyNutritionSummary {
        val dayMeals = meals.filter { it.dateKey == dateKey }
        val dayWater = waterLogs.filter { it.dateKey == dateKey }

        val totalCals = dayMeals.sumOf { it.calories }
        val totalProtein = dayMeals.sumOf { it.proteinGrams }
        val totalCarbs = dayMeals.sumOf { it.carbsGrams }
        val totalFat = dayMeals.sumOf { it.fatGrams }
        val totalFiber = dayMeals.sumOf { it.fiberGrams }
        val totalSugar = dayMeals.sumOf { it.sugarGrams }
        val totalSodium = dayMeals.sumOf { it.sodiumMg }
        val totalWaterMl = dayWater.sumOf { it.amountMl }
        val avgConfidence = if (dayMeals.isEmpty()) 100 else dayMeals.map { it.confidenceScore }.average().roundToInt()

        return DailyNutritionSummary(
            dateKey = dateKey,
            totalCalories = totalCals,
            calorieGoal = profile.dailyCalorieGoal,
            remainingCalories = profile.dailyCalorieGoal - totalCals,
            totalProteinGrams = (totalProtein * 10.0).roundToInt() / 10.0,
            proteinGoalGrams = profile.dailyProteinGrams,
            totalCarbsGrams = (totalCarbs * 10.0).roundToInt() / 10.0,
            carbsGoalGrams = profile.dailyCarbsGrams,
            totalFatGrams = (totalFat * 10.0).roundToInt() / 10.0,
            fatGoalGrams = profile.dailyFatGrams,
            totalFiberGrams = (totalFiber * 10.0).roundToInt() / 10.0,
            fiberGoalGrams = profile.dailyFiberGrams,
            totalSugarGrams = (totalSugar * 10.0).roundToInt() / 10.0,
            totalSodiumMg = totalSodium,
            totalWaterMl = totalWaterMl,
            waterGoalMl = max(500, min(10000, profile.dailyWaterMlGoal)),
            averageConfidence = avgConfidence
        )
    }
}
