package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.GroceryItem
import com.example.data.model.MealEntry
import com.example.data.model.MealPlanItem
import com.example.data.model.NutritionCalculator
import com.example.data.model.UserProfile
import com.example.data.model.WaterLog
import com.example.data.model.WeightLog
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NutriPulseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- 1. User Profile ---
    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.GET, path)
                throw e
            }
    }

    suspend fun getUserProfile(userId: String): Result<UserProfile?> {
        val path = "users/$userId"
        return try {
            val snap = db.collection("users").document(userId).get().await()
            val profile = if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(profile.toUpdateMap()).await()
            } else {
                docRef.set(profile.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Ensures a valid [UserProfile] document exists in Firestore for the signed-in Google user.
     * If the user is signing up for the first time, creates their personalized profile using
     * their Google display name and selected onboarding preferences.
     */
    suspend fun ensureUserProfileExists(
        displayName: String = "",
        goalType: String = "lose",
        dietaryPreference: String = "High Protein",
        activityLevel: String = "moderate"
    ): Result<UserProfile> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existingProfile = existingSnap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = uid)
                Result.success(existingProfile)
            } else {
                val safeName = displayName.trim()
                    .ifBlank { auth.currentUser?.displayName?.trim().orEmpty() }
                    .ifBlank { auth.currentUser?.email?.substringBefore("@")?.trim().orEmpty() }
                    .ifBlank { "NutriPulse Member" }
                    .take(100)
                val calculated = NutritionCalculator.calculatePersonalizedGoals(
                    age = 30,
                    gender = "female",
                    heightCm = 168.0,
                    currentWeightKg = 68.0,
                    targetWeightKg = 62.0,
                    activityLevel = activityLevel,
                    goalType = goalType,
                    dietaryPreference = dietaryPreference
                ).copy(userId = uid, displayName = safeName)
                docRef.set(calculated.toCreateMap(uid)).await()
                Result.success(calculated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 2. Meals (Daily Food Diary) ---
    fun observeMeals(userId: String): Flow<List<MealEntry>> {
        val path = "users/$userId/meals"
        return db.collection("users").document(userId).collection("meals")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MealEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    suspend fun addMeal(meal: MealEntry): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("meals")
        return try {
            val docRef = colRef.add(meal.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateMeal(meal: MealEntry): Result<Unit> {
        val uid = requireUserId()
        require(meal.id.isNotBlank()) { "Meal ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("meals").document(meal.id)
        return try {
            docRef.update(meal.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMeal(mealId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("meals").document(mealId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getMealById(targetUserId: String, mealId: String): Result<MealEntry?> {
        val docRef = db.collection("users").document(targetUserId).collection("meals").document(mealId)
        return try {
            val snap = docRef.get(com.google.firebase.firestore.Source.SERVER).await()
            Result.success(snap.toObject(MealEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    // --- 3. Water Logs ---
    fun observeWaterLogs(userId: String): Flow<List<WaterLog>> {
        val path = "users/$userId/water_logs"
        return db.collection("users").document(userId).collection("water_logs")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(WaterLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    suspend fun addWaterLog(waterLog: WaterLog): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("water_logs")
        return try {
            val docRef = colRef.add(waterLog.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteWaterLog(logId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("water_logs").document(logId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 4. Weight Logs ---
    fun observeWeightLogs(userId: String): Flow<List<WeightLog>> {
        val path = "users/$userId/weight_logs"
        return db.collection("users").document(userId).collection("weight_logs")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(WeightLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.dateKey }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    suspend fun addWeightLog(weightLog: WeightLog): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("weight_logs")
        return try {
            val docRef = colRef.add(weightLog.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteWeightLog(logId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("weight_logs").document(logId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 5. Meal Plans & Recipes ---
    fun observeMealPlans(userId: String): Flow<List<MealPlanItem>> {
        val path = "users/$userId/meal_plans"
        return db.collection("users").document(userId).collection("meal_plans")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MealPlanItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    suspend fun addMealPlanItem(item: MealPlanItem): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("meal_plans")
        return try {
            val docRef = colRef.add(item.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateMealPlanItem(item: MealPlanItem): Result<Unit> {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "MealPlanItem ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("meal_plans").document(item.id)
        return try {
            docRef.update(item.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteMealPlanItem(planId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("meal_plans").document(planId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- 6. Grocery List ---
    fun observeGroceryItems(userId: String): Flow<List<GroceryItem>> {
        val path = "users/$userId/grocery_items"
        return db.collection("users").document(userId).collection("grocery_items")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(GroceryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<GroceryItem> { it.checked }.thenBy { it.category }.thenBy { it.name })
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    suspend fun addGroceryItem(item: GroceryItem): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("grocery_items")
        return try {
            val docRef = colRef.add(item.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateGroceryItem(item: GroceryItem): Result<Unit> {
        val uid = requireUserId()
        require(item.id.isNotBlank()) { "GroceryItem ID must not be blank" }
        val docRef = db.collection("users").document(uid).collection("grocery_items").document(item.id)
        return try {
            docRef.update(item.toUpdateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteGroceryItem(itemId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("grocery_items").document(itemId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
