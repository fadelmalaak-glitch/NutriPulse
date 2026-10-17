package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DailyNutritionSummary
import com.example.data.model.GroceryItem
import com.example.data.model.MealEntry
import com.example.data.model.MealPlanItem
import com.example.data.model.NutritionCalculator
import com.example.data.model.UserProfile
import com.example.data.model.WaterLog
import com.example.data.model.WeightLog
import com.example.data.remote.OpenFoodFactsClient
import com.example.data.repository.NutriPulseRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Streaming(val partialText: String) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "coach"
    val text: String,
    val isStreaming: Boolean = false
)

class NutriPulseViewModel(
    private val repository: NutriPulseRepository,
    private val currentUserId: String
) : ViewModel() {

    private companion object {
        const val TAG = "NutriPulseVM"
        const val STOP_TIMEOUT_MS = 5000L
        const val AI_TIMEOUT_MS = 60_000L
    }

    // --- Selected Date for Diary & Dashboard ---
    private val _selectedDateKey = MutableStateFlow(NutritionCalculator.todayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    fun selectDateKey(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    // --- Firestore Real-Time Streams (Two-Tier Error Handling) ---
    val profileState: StateFlow<UiState<UserProfile>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile>> { profile ->
            UiState.Success(profile ?: UserProfile(userId = currentUserId))
        }
        .catch { e ->
            Log.w(TAG, "Error observing profile", e)
            emit(UiState.Error(e.message ?: "Failed to load nutrition profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val mealsState: StateFlow<UiState<List<MealEntry>>> = repository.observeMeals(currentUserId)
        .map<List<MealEntry>, UiState<List<MealEntry>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing meals", e)
            emit(UiState.Error(e.message ?: "Failed to load food diary"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val waterLogsState: StateFlow<UiState<List<WaterLog>>> = repository.observeWaterLogs(currentUserId)
        .map<List<WaterLog>, UiState<List<WaterLog>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing water logs", e)
            emit(UiState.Error(e.message ?: "Failed to load water logs"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val weightLogsState: StateFlow<UiState<List<WeightLog>>> = repository.observeWeightLogs(currentUserId)
        .map<List<WeightLog>, UiState<List<WeightLog>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing weight logs", e)
            emit(UiState.Error(e.message ?: "Failed to load weight logs"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val mealPlansState: StateFlow<UiState<List<MealPlanItem>>> = repository.observeMealPlans(currentUserId)
        .map<List<MealPlanItem>, UiState<List<MealPlanItem>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing meal plans", e)
            emit(UiState.Error(e.message ?: "Failed to load meal plans"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val groceryItemsState: StateFlow<UiState<List<GroceryItem>>> = repository.observeGroceryItems(currentUserId)
        .map<List<GroceryItem>, UiState<List<GroceryItem>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing grocery items", e)
            emit(UiState.Error(e.message ?: "Failed to load grocery list"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    // --- Dynamic Daily Nutrition Summary (Auto-updates when meals, water, profile, or date changes) ---
    val dailySummary: StateFlow<DailyNutritionSummary> = combine(
        _selectedDateKey,
        profileState,
        mealsState,
        waterLogsState
    ) { dateKey, profState, mState, wState ->
        val profile = (profState as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
        val meals = (mState as? UiState.Success)?.data ?: emptyList()
        val water = (wState as? UiState.Success)?.data ?: emptyList()
        NutritionCalculator.computeDailySummary(dateKey, profile, meals, water)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        NutritionCalculator.computeDailySummary(
            NutritionCalculator.todayDateKey(),
            UserProfile(userId = currentUserId),
            emptyList(),
            emptyList()
        )
    )

    // --- Feedback Banner / Status Message ---
    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun dismissStatusBanner() {
        _statusBanner.value = null
    }

    // --- 1. AI Food Estimation Schema & Lazy Model (gemini-3.8-flash) ---
    private val foodEstimationSchema = Schema.obj(
        properties = mapOf(
            "foodName" to Schema.string(description = "Concise, appetizing name of the food or meal"),
            "mealType" to Schema.enumeration(
                values = listOf("breakfast", "lunch", "dinner", "snack"),
                description = "Best matching meal category"
            ),
            "portionDescription" to Schema.string(description = "Estimated portion size with weight in grams, e.g. '1 bowl (340g)'"),
            "calories" to Schema.integer(description = "Total estimated kilocalories"),
            "proteinGrams" to Schema.double(description = "Estimated protein in grams"),
            "carbsGrams" to Schema.double(description = "Estimated total carbohydrates in grams"),
            "fatGrams" to Schema.double(description = "Estimated total fat in grams"),
            "fiberGrams" to Schema.double(description = "Estimated dietary fiber in grams"),
            "sugarGrams" to Schema.double(description = "Estimated total sugars in grams"),
            "sodiumMg" to Schema.integer(description = "Estimated sodium in milligrams"),
            "confidenceScore" to Schema.integer(description = "Nutritional estimation confidence score from 60 to 99"),
            "ingredientsSummary" to Schema.string(description = "Detailed list of detected ingredients with estimated gram weights"),
            "aiNotes" to Schema.string(description = "Key micronutrients (iron, potassium, omega-3, vitamins) and satiety insight")
        )
    )

    private val foodAnalyzerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = foodEstimationSchema
            }
        )
    }

    // --- 2. AI Meal Plan Schema & Lazy Model (gemini-3.8-flash) ---
    private val mealPlanSchema = Schema.obj(
        properties = mapOf(
            "meals" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "mealType" to Schema.enumeration(values = listOf("breakfast", "lunch", "dinner", "snack")),
                        "title" to Schema.string(description = "Recipe title"),
                        "calories" to Schema.integer(description = "Calories per serving"),
                        "proteinGrams" to Schema.integer(description = "Protein in grams"),
                        "carbsGrams" to Schema.integer(description = "Carbohydrates in grams"),
                        "fatGrams" to Schema.integer(description = "Fat in grams"),
                        "prepTimeMinutes" to Schema.integer(description = "Prep and cook time in minutes"),
                        "ingredientsText" to Schema.string(description = "Ingredients with quantities"),
                        "instructionsText" to Schema.string(description = "Concise step-by-step preparation instructions")
                    )
                ),
                description = "List of 4 daily meals (breakfast, lunch, dinner, snack)"
            ),
            "groceryItems" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(description = "Ingredient name"),
                        "quantity" to Schema.string(description = "Quantity needed, e.g. '250g' or '2 avocados'"),
                        "category" to Schema.enumeration(
                            values = listOf("Produce", "Protein", "Grains", "Dairy & Alt", "Pantry")
                        )
                    )
                ),
                description = "Consolidated grocery list for the planned meals"
            )
        )
    )

    private val mealPlannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = mealPlanSchema
            }
        )
    }

    // --- 3. Conversational AI Coach Lazy Model (gemini-3.8-flash) ---
    private val coachModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    // --- AI Food Analysis State ---
    private val _aiFoodAnalysisState = MutableStateFlow<UiState<MealEntry>>(UiState.Idle)
    val aiFoodAnalysisState: StateFlow<UiState<MealEntry>> = _aiFoodAnalysisState.asStateFlow()

    fun resetAiFoodAnalysis() {
        _aiFoodAnalysisState.value = UiState.Idle
    }

    fun analyzeFoodWithAi(
        descriptionPrompt: String,
        photoBitmap: Bitmap? = null,
        logMethod: String = if (photoBitmap != null) "photo" else "chat",
        preferredMealType: String? = null
    ) {
        if (descriptionPrompt.isBlank() && photoBitmap == null) {
            _aiFoodAnalysisState.value = UiState.Error("Please provide a food description, voice note, or photo to analyze.")
            return
        }
        viewModelScope.launch {
            _aiFoodAnalysisState.value = UiState.Loading
            try {
                val systemPrompt = buildString {
                    append("You are an expert registered dietitian and AI nutritionist. ")
                    append("Analyze the provided meal photo and/or text description accurately. ")
                    append("Break down all visible or described ingredients with realistic gram weights, ")
                    append("calculate total calories, protein, carbs, fat, fiber, sugar, and sodium, ")
                    append("and provide a realistic confidenceScore (0-100) and micronutrient highlights. ")
                    if (descriptionPrompt.isNotBlank()) {
                        append("User description / corrections: $descriptionPrompt")
                    }
                }

                val response = withTimeout(AI_TIMEOUT_MS) {
                    if (photoBitmap != null) {
                        foodAnalyzerModel.generateContent(
                            content {
                                image(photoBitmap)
                                text(systemPrompt)
                            }
                        )
                    } else {
                        foodAnalyzerModel.generateContent(systemPrompt)
                    }
                }

                val jsonText = response.text
                if (jsonText.isNullOrBlank()) {
                    _aiFoodAnalysisState.value = UiState.Error("Model returned an empty nutrition analysis.")
                    return@launch
                }

                val json = JSONObject(jsonText)
                val parsedMeal = MealEntry(
                    userId = currentUserId,
                    dateKey = _selectedDateKey.value,
                    mealType = preferredMealType ?: json.optString("mealType", "lunch"),
                    foodName = json.optString("foodName", "Analyzed Meal").take(150),
                    portionDescription = json.optString("portionDescription", "1 serving").take(120),
                    portionMultiplier = 1.0,
                    calories = json.optInt("calories", 350).coerceIn(0, 15000),
                    proteinGrams = json.optDouble("proteinGrams", 20.0).coerceIn(0.0, 1000.0),
                    carbsGrams = json.optDouble("carbsGrams", 35.0).coerceIn(0.0, 2000.0),
                    fatGrams = json.optDouble("fatGrams", 12.0).coerceIn(0.0, 1000.0),
                    fiberGrams = json.optDouble("fiberGrams", 5.0).coerceIn(0.0, 300.0),
                    sugarGrams = json.optDouble("sugarGrams", 4.0).coerceIn(0.0, 1000.0),
                    sodiumMg = json.optInt("sodiumMg", 380).coerceIn(0, 50000),
                    confidenceScore = json.optInt("confidenceScore", 90).coerceIn(0, 100),
                    logMethod = logMethod,
                    ingredientsSummary = json.optString("ingredientsSummary", "").take(500),
                    aiNotes = json.optString("aiNotes", "").take(500)
                )

                _aiFoodAnalysisState.value = UiState.Success(parsedMeal)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI food analysis timed out", e)
                _aiFoodAnalysisState.value = UiState.Error("AI nutrition analysis timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI food analysis failed", e)
                _aiFoodAnalysisState.value = UiState.Error(e.localizedMessage ?: "Failed to analyze food with Gemini AI.")
            }
        }
    }

    // --- Barcode Lookup (Live Open Food Facts REST API + Gemini AI Enrichment Fallback) ---
    fun lookupBarcode(barcodeInput: String, productHint: String = "") {
        val clean = barcodeInput.trim()
        if (clean.isBlank()) {
            _aiFoodAnalysisState.value = UiState.Error("Please enter or select a barcode number.")
            return
        }
        viewModelScope.launch {
            _aiFoodAnalysisState.value = UiState.Loading
            val apiResult = OpenFoodFactsClient.lookupBarcode(clean)
            if (apiResult.isSuccess) {
                val item = apiResult.getOrThrow()
                val entry = MealEntry(
                    userId = currentUserId,
                    dateKey = _selectedDateKey.value,
                    mealType = "snack",
                    foodName = item.productName,
                    portionDescription = "1 serving (${item.servingSize})",
                    portionMultiplier = 1.0,
                    calories = item.calories,
                    proteinGrams = item.proteinGrams,
                    carbsGrams = item.carbsGrams,
                    fatGrams = item.fatGrams,
                    fiberGrams = item.fiberGrams,
                    sugarGrams = item.sugarGrams,
                    sodiumMg = item.sodiumMg,
                    confidenceScore = 98,
                    logMethod = "barcode",
                    ingredientsSummary = item.ingredientsText,
                    aiNotes = "Verified via Open Food Facts barcode database (EAN/UPC: ${item.barcode}).",
                    barcode = item.barcode
                )
                _aiFoodAnalysisState.value = UiState.Success(entry)
            } else {
                // Fallback to Gemini AI structured lookup when a regional barcode isn't in Open Food Facts
                val fallbackPrompt = if (productHint.isNotBlank()) {
                    "Barcode $clean ($productHint). Estimate accurate nutrition per serving."
                } else {
                    "Packaged food item with barcode $clean. Estimate standard single-serving nutrition."
                }
                analyzeFoodWithAi(
                    descriptionPrompt = fallbackPrompt,
                    photoBitmap = null,
                    logMethod = "barcode"
                )
            }
        }
    }

    // --- Save / Update / Scale / Delete Meals ---
    fun saveMealEntry(meal: MealEntry, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val result = if (meal.id.isBlank()) {
                repository.addMeal(meal.copy(dateKey = _selectedDateKey.value))
            } else {
                repository.updateMeal(meal).map { meal.id }
            }
            if (result.isSuccess) {
                _statusBanner.value = "Saved \"${meal.foodName}\" (${meal.calories} kcal)"
                _aiFoodAnalysisState.value = UiState.Idle
                onSaved()
            } else {
                _statusBanner.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to save meal"
            }
        }
    }

    fun scaleMealPortion(meal: MealEntry, newMultiplier: Double) {
        viewModelScope.launch {
            val scaled = NutritionCalculator.scaleMealPortion(meal, newMultiplier)
            val result = repository.updateMeal(scaled)
            if (result.isSuccess) {
                _statusBanner.value = "Updated portion to ${scaled.portionMultiplier}x (${scaled.calories} kcal)"
            }
        }
    }

    fun deleteMealEntry(mealId: String) {
        viewModelScope.launch {
            repository.deleteMeal(mealId)
            _statusBanner.value = "Meal entry removed from diary"
        }
    }

    // --- Water Intake Tracking ---
    fun logWater(amountMl: Int, beverageType: String = "water") {
        viewModelScope.launch {
            val result = repository.addWaterLog(
                WaterLog(
                    userId = currentUserId,
                    dateKey = _selectedDateKey.value,
                    amountMl = amountMl,
                    beverageType = beverageType
                )
            )
            if (result.isSuccess) {
                _statusBanner.value = "Logged +${amountMl}ml $beverageType"
            }
        }
    }

    fun deleteWaterLog(logId: String) {
        viewModelScope.launch {
            repository.deleteWaterLog(logId)
        }
    }

    // --- Weight & Body Progress Tracking ---
    fun logWeightCheckIn(weightKg: Double, bodyFatPct: Double, note: String) {
        viewModelScope.launch {
            val result = repository.addWeightLog(
                WeightLog(
                    userId = currentUserId,
                    dateKey = _selectedDateKey.value,
                    weightKg = weightKg,
                    bodyFatPct = bodyFatPct,
                    note = note
                )
            )
            if (result.isSuccess) {
                // Also sync currentWeightKg in UserProfile and recalculate goals
                val currentProfile = (profileState.value as? UiState.Success)?.data
                if (currentProfile != null) {
                    val updated = NutritionCalculator.calculatePersonalizedGoals(
                        age = currentProfile.age,
                        gender = currentProfile.gender,
                        heightCm = currentProfile.heightCm,
                        currentWeightKg = weightKg,
                        targetWeightKg = currentProfile.targetWeightKg,
                        activityLevel = currentProfile.activityLevel,
                        goalType = currentProfile.goalType,
                        dietaryPreference = currentProfile.dietaryPreference
                    ).copy(displayName = currentProfile.displayName)
                    repository.saveUserProfile(updated)
                }
                _statusBanner.value = "Logged weight check-in: ${weightKg}kg"
            }
        }
    }

    fun deleteWeightLog(logId: String) {
        viewModelScope.launch {
            repository.deleteWeightLog(logId)
        }
    }

    // --- Personalized Nutrition Profile & Goal Calculation ---
    fun updatePersonalizedProfile(
        displayName: String,
        age: Int,
        gender: String,
        heightCm: Double,
        currentWeightKg: Double,
        targetWeightKg: Double,
        activityLevel: String,
        goalType: String,
        dietaryPreference: String
    ) {
        viewModelScope.launch {
            val calculated = NutritionCalculator.calculatePersonalizedGoals(
                age = age,
                gender = gender,
                heightCm = heightCm,
                currentWeightKg = currentWeightKg,
                targetWeightKg = targetWeightKg,
                activityLevel = activityLevel,
                goalType = goalType,
                dietaryPreference = dietaryPreference
            ).copy(displayName = displayName)

            val result = repository.saveUserProfile(calculated)
            if (result.isSuccess) {
                _statusBanner.value = "Goals updated: ${calculated.dailyCalorieGoal} kcal/day (${calculated.dailyProteinGrams}g P / ${calculated.dailyCarbsGrams}g C / ${calculated.dailyFatGrams}g F)"
            } else {
                _statusBanner.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to save profile"
            }
        }
    }

    fun initializeAccountProfile(
        displayName: String,
        goalType: String = "lose",
        dietaryPreference: String = "High Protein",
        activityLevel: String = "moderate",
        isSignUp: Boolean = false
    ) {
        viewModelScope.launch {
            val result = repository.ensureUserProfileExists(
                displayName = displayName,
                goalType = goalType,
                dietaryPreference = dietaryPreference,
                activityLevel = activityLevel
            )
            if (result.isSuccess && isSignUp) {
                val created = result.getOrNull()
                if (created != null) {
                    _statusBanner.value = "Welcome to NutriPulse, ${created.displayName}! Daily target set to ${created.dailyCalorieGoal} kcal."
                }
            }
        }
    }

    // --- AI Meal Planner & Grocery List Generation ---
    private val _mealPlanGenerationState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val mealPlanGenerationState: StateFlow<UiState<String>> = _mealPlanGenerationState.asStateFlow()

    fun generateAiMealPlan(dayLabel: String = "Today", customPreferenceNote: String = "") {
        viewModelScope.launch {
            _mealPlanGenerationState.value = UiState.Loading
            try {
                val profile = (profileState.value as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
                val prompt = buildString {
                    append("Create a personalized 1-day meal plan (breakfast, lunch, dinner, snack) for day '$dayLabel'. ")
                    append("Target daily calories: ${profile.dailyCalorieGoal} kcal. ")
                    append("Target macros: ${profile.dailyProteinGrams}g protein, ${profile.dailyCarbsGrams}g carbs, ${profile.dailyFatGrams}g fat. ")
                    append("Dietary style: ${profile.dietaryPreference}. Goal: ${profile.goalType}. ")
                    if (customPreferenceNote.isNotBlank()) {
                        append("Additional user request: $customPreferenceNote. ")
                    }
                    append("Include practical prep times, exact ingredient quantities, step-by-step instructions, and a consolidated grocery list.")
                }

                val response = withTimeout(AI_TIMEOUT_MS) {
                    mealPlannerModel.generateContent(prompt)
                }
                val jsonText = response.text ?: throw IllegalStateException("Empty meal plan response from Gemini.")
                val json = JSONObject(jsonText)

                val mealsArray = json.getJSONArray("meals")
                for (i in 0 until mealsArray.length()) {
                    val m = mealsArray.getJSONObject(i)
                    val planItem = MealPlanItem(
                        userId = currentUserId,
                        dayOfWeek = dayLabel,
                        mealType = m.optString("mealType", "lunch"),
                        title = m.optString("title", "Balanced Meal"),
                        calories = m.optInt("calories", 450),
                        proteinGrams = m.optInt("proteinGrams", 35),
                        carbsGrams = m.optInt("carbsGrams", 45),
                        fatGrams = m.optInt("fatGrams", 15),
                        prepTimeMinutes = m.optInt("prepTimeMinutes", 20),
                        ingredientsText = m.optString("ingredientsText", "Seasonal ingredients"),
                        instructionsText = m.optString("instructionsText", "Prepare fresh and serve."),
                        isCompleted = false
                    )
                    repository.addMealPlanItem(planItem)
                }

                val groceryArray = json.optJSONArray("groceryItems")
                if (groceryArray != null) {
                    for (i in 0 until groceryArray.length()) {
                        val g = groceryArray.getJSONObject(i)
                        repository.addGroceryItem(
                            GroceryItem(
                                userId = currentUserId,
                                name = g.optString("name", "Fresh Ingredient"),
                                quantity = g.optString("quantity", "1 unit"),
                                category = g.optString("category", "Produce"),
                                checked = false
                            )
                        )
                    }
                }

                _mealPlanGenerationState.value = UiState.Success("Generated 4 tailored meals and synced grocery items!")
                _statusBanner.value = "AI Meal Plan & Grocery List ready!"
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI meal plan generation timed out", e)
                _mealPlanGenerationState.value = UiState.Error("Meal plan generation timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI meal plan generation failed", e)
                _mealPlanGenerationState.value = UiState.Error(e.localizedMessage ?: "Failed to generate meal plan.")
            }
        }
    }

    fun logPlannedMealToDiary(item: MealPlanItem) {
        viewModelScope.launch {
            val meal = MealEntry(
                userId = currentUserId,
                dateKey = _selectedDateKey.value,
                mealType = item.mealType,
                foodName = item.title,
                portionDescription = "1 planned recipe serving (${item.prepTimeMinutes}m prep)",
                portionMultiplier = 1.0,
                calories = item.calories,
                proteinGrams = item.proteinGrams.toDouble(),
                carbsGrams = item.carbsGrams.toDouble(),
                fatGrams = item.fatGrams.toDouble(),
                fiberGrams = 6.0,
                sugarGrams = 4.0,
                sodiumMg = 420,
                confidenceScore = 97,
                logMethod = "chat",
                ingredientsSummary = item.ingredientsText,
                aiNotes = "Logged directly from your personalized AI Meal Plan."
            )
            repository.addMeal(meal)
            if (item.id.isNotBlank()) {
                repository.updateMealPlanItem(item.copy(isCompleted = true))
            }
            _statusBanner.value = "Logged \"${item.title}\" to today's diary!"
        }
    }

    fun deleteMealPlanItem(planId: String) {
        viewModelScope.launch {
            repository.deleteMealPlanItem(planId)
        }
    }

    fun addGroceryItem(name: String, quantity: String, category: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addGroceryItem(
                GroceryItem(
                    userId = currentUserId,
                    name = name.trim(),
                    quantity = quantity.trim().ifBlank { "1 unit" },
                    category = category,
                    checked = false
                )
            )
        }
    }

    fun toggleGroceryItem(item: GroceryItem) {
        viewModelScope.launch {
            repository.updateGroceryItem(item.copy(checked = !item.checked))
        }
    }

    fun deleteGroceryItem(itemId: String) {
        viewModelScope.launch {
            repository.deleteGroceryItem(itemId)
        }
    }

    // --- Streaming AI Nutrition Coach ---
    private val _coachMessages = MutableStateFlow(
        listOf(
            ChatMessage(
                role = "coach",
                text = "Hi! I'm your NutriPulse AI Coach. I have live context on your daily calorie budget, macronutrient targets, and food diary. Ask me what to eat next, how to hit your protein goal, or how to optimize recovery!"
            )
        )
    )
    val coachMessages: StateFlow<List<ChatMessage>> = _coachMessages.asStateFlow()

    private val _coachUiState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val coachUiState: StateFlow<UiState<String>> = _coachUiState.asStateFlow()

    fun askNutritionCoach(userQuestion: String) {
        val cleanQuestion = userQuestion.trim()
        if (cleanQuestion.isBlank()) return

        val userMsg = ChatMessage(role = "user", text = cleanQuestion)
        val streamingId = java.util.UUID.randomUUID().toString()
        val placeholderCoachMsg = ChatMessage(id = streamingId, role = "coach", text = "Thinking...", isStreaming = true)
        _coachMessages.value = _coachMessages.value + userMsg + placeholderCoachMsg

        viewModelScope.launch {
            _coachUiState.value = UiState.Loading
            val accumulated = StringBuilder()
            try {
                val summary = dailySummary.value
                val profile = (profileState.value as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
                val todayMeals = ((mealsState.value as? UiState.Success)?.data ?: emptyList())
                    .filter { it.dateKey == summary.dateKey }
                    .joinToString("; ") { "${it.mealType}: ${it.foodName} (${it.calories} kcal, ${it.proteinGrams}g P)" }
                    .ifBlank { "No meals logged yet for ${summary.dateKey}" }

                val contextualPrompt = buildString {
                    append("You are NutriPulse AI, an empathetic, evidence-based sports & clinical nutrition coach.\n")
                    append("User Profile: ${profile.displayName}, Goal=${profile.goalType}, Diet=${profile.dietaryPreference}, ")
                    append("Current Weight=${profile.currentWeightKg}kg, Target Weight=${profile.targetWeightKg}kg.\n")
                    append("Today's Live Progress (${summary.dateKey}): ")
                    append("${summary.totalCalories} / ${summary.calorieGoal} kcal (${summary.remainingCalories} kcal remaining), ")
                    append("Protein: ${summary.totalProteinGrams}g / ${summary.proteinGoalGrams}g, ")
                    append("Carbs: ${summary.totalCarbsGrams}g / ${summary.carbsGoalGrams}g, ")
                    append("Fat: ${summary.totalFatGrams}g / ${summary.fatGoalGrams}g, ")
                    append("Fiber: ${summary.totalFiberGrams}g / ${summary.fiberGoalGrams}g, ")
                    append("Water: ${summary.totalWaterMl}ml / ${summary.waterGoalMl}ml.\n")
                    append("Meals logged today: $todayMeals.\n\n")
                    append("User Question: $cleanQuestion\n")
                    append("Provide specific, actionable, encouraging advice with concrete food portions and macro estimates.")
                }

                withTimeout(AI_TIMEOUT_MS) {
                    coachModel.generateContentStream(contextualPrompt).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        accumulated.append(chunkText)
                        val currentText = accumulated.toString()
                        _coachUiState.value = UiState.Streaming(currentText)
                        _coachMessages.value = _coachMessages.value.map { msg ->
                            if (msg.id == streamingId) msg.copy(text = currentText, isStreaming = true) else msg
                        }
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotBlank()) {
                    _coachUiState.value = UiState.Success(finalReply)
                    _coachMessages.value = _coachMessages.value.map { msg ->
                        if (msg.id == streamingId) msg.copy(text = finalReply, isStreaming = false) else msg
                    }
                } else {
                    val errText = "Model returned an empty coaching response."
                    _coachUiState.value = UiState.Error(errText)
                    _coachMessages.value = _coachMessages.value.map { msg ->
                        if (msg.id == streamingId) msg.copy(text = errText, isStreaming = false) else msg
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Coach streaming timed out", e)
                val msgText = "Coaching response timed out after 60 seconds. Please try again."
                _coachUiState.value = UiState.Error(msgText)
                _coachMessages.value = _coachMessages.value.map { msg ->
                    if (msg.id == streamingId) msg.copy(text = msgText, isStreaming = false) else msg
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Coach streaming failed", e)
                val msgText = e.localizedMessage ?: "Failed to reach AI Nutrition Coach."
                _coachUiState.value = UiState.Error(msgText)
                _coachMessages.value = _coachMessages.value.map { msg ->
                    if (msg.id == streamingId) msg.copy(text = msgText, isStreaming = false) else msg
                }
            }
        }
    }
}
