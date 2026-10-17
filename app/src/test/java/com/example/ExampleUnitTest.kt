package com.example

import com.example.data.model.MealEntry
import com.example.data.model.NutritionCalculator
import com.example.data.model.WaterLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun mifflinStJeorCalculator_computesAccurateCalorieAndMacroTargets() {
        val profile = NutritionCalculator.calculatePersonalizedGoals(
            age = 30,
            gender = "female",
            heightCm = 165.0,
            currentWeightKg = 65.0,
            targetWeightKg = 60.0,
            activityLevel = "moderate",
            goalType = "lose",
            dietaryPreference = "High Protein"
        )
        assertTrue(profile.dailyCalorieGoal in 1400..2100)
        assertTrue(profile.dailyProteinGrams >= 110)
        assertTrue(profile.dailyWaterMlGoal >= 2000)
    }

    @Test
    fun scaleMealPortion_scalesCaloriesAndMacrosProportionally() {
        val baseMeal = MealEntry(
            foodName = "Grilled Chicken Rice Bowl",
            portionMultiplier = 1.0,
            calories = 500,
            proteinGrams = 40.0,
            carbsGrams = 50.0,
            fatGrams = 15.0,
            fiberGrams = 6.0,
            sugarGrams = 4.0,
            sodiumMg = 400
        )

        val doubled = NutritionCalculator.scaleMealPortion(baseMeal, 2.0)
        assertEquals(2.0, doubled.portionMultiplier, 0.01)
        assertEquals(1000, doubled.calories)
        assertEquals(80.0, doubled.proteinGrams, 0.1)

        val halved = NutritionCalculator.scaleMealPortion(doubled, 0.5)
        assertEquals(0.5, halved.portionMultiplier, 0.01)
        assertEquals(250, halved.calories)
        assertEquals(20.0, halved.proteinGrams, 0.1)
    }

    @Test
    fun computeDailySummary_updatesRemainingCaloriesAndMacros() {
        val profile = NutritionCalculator.calculatePersonalizedGoals(
            age = 28,
            gender = "male",
            heightCm = 180.0,
            currentWeightKg = 80.0,
            targetWeightKg = 75.0,
            activityLevel = "moderate",
            goalType = "maintain",
            dietaryPreference = "Balanced"
        )
        val dateKey = "2026-10-17"
        val meals = listOf(
            MealEntry(dateKey = dateKey, calories = 600, proteinGrams = 45.0, carbsGrams = 60.0, fatGrams = 20.0, confidenceScore = 96),
            MealEntry(dateKey = dateKey, calories = 700, proteinGrams = 50.0, carbsGrams = 70.0, fatGrams = 22.0, confidenceScore = 92)
        )
        val water = listOf(
            WaterLog(dateKey = dateKey, amountMl = 500),
            WaterLog(dateKey = dateKey, amountMl = 350)
        )

        val summary = NutritionCalculator.computeDailySummary(dateKey, profile, meals, water)
        assertEquals(1300, summary.totalCalories)
        assertEquals(profile.dailyCalorieGoal - 1300, summary.remainingCalories)
        assertEquals(95.0, summary.totalProteinGrams, 0.1)
        assertEquals(850, summary.totalWaterMl)
        assertEquals(94, summary.averageConfidence)
    }
}
