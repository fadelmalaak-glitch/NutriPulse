package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.GroceryItem
import com.example.data.model.MealEntry
import com.example.data.model.NutritionCalculator
import com.example.data.model.WaterLog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutriPulseRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndGetUserProfile_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NutriPulseRepository(firestore, auth)

        val calculated = NutritionCalculator.calculatePersonalizedGoals(
            age = 29,
            gender = "female",
            heightCm = 166.0,
            currentWeightKg = 65.0,
            targetWeightKg = 60.0,
            activityLevel = "moderate",
            goalType = "lose",
            dietaryPreference = "High Protein"
        ).copy(displayName = "Alice Test")

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.saveUserProfile(calculated) }
        assertTrue("Profile save should succeed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

        val fetchedResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getUserProfile(uid) }
        assertTrue(fetchedResult.isSuccess)
        val profile = fetchedResult.getOrNull()
        assertNotNull(profile)
        assertEquals("Alice Test", profile?.displayName)
        assertEquals(calculated.dailyCalorieGoal, profile?.dailyCalorieGoal)
    }

    @Test
    fun addAndObserveMeals_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NutriPulseRepository(firestore, auth)

        val meal = MealEntry(
            dateKey = "2026-10-17",
            mealType = "breakfast",
            foodName = "Avocado Poached Egg Toast",
            portionDescription = "2 slices (240g)",
            portionMultiplier = 1.0,
            calories = 440,
            proteinGrams = 19.5,
            carbsGrams = 34.0,
            fatGrams = 24.0,
            fiberGrams = 7.5,
            sugarGrams = 2.0,
            sodiumMg = 390,
            confidenceScore = 96,
            logMethod = "photo",
            ingredientsSummary = "Sourdough bread, 2 poached eggs, half avocado, chili flakes",
            aiNotes = "High in healthy monounsaturated fats and choline."
        )

        val addResult = withTimeout(DEFAULT_TIMEOUT_MS) { repository.addMeal(meal) }
        assertTrue("Add meal should succeed: ${addResult.exceptionOrNull()}", addResult.isSuccess)
        val mealId = addResult.getOrThrow()

        val emittedMeals = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMeals(uid).first { list -> list.any { it.id == mealId } }
        }
        assertTrue(emittedMeals.any { it.id == mealId && it.foodName == "Avocado Poached Egg Toast" })
    }

    @Test
    fun getMealById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = NutriPulseRepository(firestore, auth)
        val mealId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addMeal(
                MealEntry(
                    dateKey = "2026-10-17",
                    mealType = "lunch",
                    foodName = "Alice Private Bowl",
                    calories = 510
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = NutriPulseRepository(firestore, auth)
        val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getMealById(aliceUid, mealId)
        }
        assertTrue("Cross-user access must fail", crossResult.isFailure)
        val ex = crossResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun observeMeals_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = NutriPulseRepository(firestore, auth)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeMeals(aliceUid).first()
            }
        } catch (e: Throwable) {
            caughtException = e
        }
        assertNotNull("Unauthenticated query must throw exception", caughtException)
        val firestoreEx = caughtException as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun waterAndGroceryCrud_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NutriPulseRepository(firestore, auth)

        val waterResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addWaterLog(WaterLog(dateKey = "2026-10-17", amountMl = 350, beverageType = "water"))
        }
        assertTrue(waterResult.isSuccess)

        val groceryResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addGroceryItem(GroceryItem(name = "Wild Alaskan Salmon", quantity = "400g", category = "Protein"))
        }
        assertTrue(groceryResult.isSuccess)
        val groceryId = groceryResult.getOrThrow()

        val groceries = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeGroceryItems(uid).first { list -> list.any { it.id == groceryId } }
        }
        assertTrue(groceries.any { it.name == "Wild Alaskan Salmon" })
    }

    @Test
    fun ensureUserProfileExists_googleSignUpAndSubsequentLogin_provisionsAndPreservesProfile() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = NutriPulseRepository(firestore, auth)

        val signUpProfileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfileExists(
                displayName = "Alice SignUp",
                goalType = "recomp",
                dietaryPreference = "Mediterranean",
                activityLevel = "active"
            )
        }
        assertTrue("Sign-up profile initialization should succeed: ${signUpProfileResult.exceptionOrNull()}", signUpProfileResult.isSuccess)
        val created = signUpProfileResult.getOrNull()
        assertNotNull(created)
        assertEquals("Alice SignUp", created?.displayName)
        assertEquals("recomp", created?.goalType)
        assertEquals("Mediterranean", created?.dietaryPreference)

        // Subsequent login should preserve existing profile without overwriting
        val subsequentLoginResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfileExists(
                displayName = "Different Name",
                goalType = "lose",
                dietaryPreference = "Low Carb",
                activityLevel = "sedentary"
            )
        }
        assertTrue(subsequentLoginResult.isSuccess)
        val preserved = subsequentLoginResult.getOrNull()
        assertEquals("Alice SignUp", preserved?.displayName)
        assertEquals("recomp", preserved?.goalType)
        assertEquals(uid, preserved?.userId)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@nutripulse.test"
        const val BOB_EMAIL = "bob@nutripulse.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
