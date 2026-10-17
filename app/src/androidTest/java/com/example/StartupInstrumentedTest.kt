package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun requireUnconfiguredBuild() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("This regression covers exports without Firebase configuration",
            FirebaseApp.getApps(context).none { it.name == FirebaseApp.DEFAULT_APP_NAME })
    }

    @Test
    fun missingFirebaseShowsSetupScreenInsteadOfCrashing() {
        requireUnconfiguredBuild()
        // This is the exact unguarded call previously evaluated by AppNavigation.
        assertThrows(IllegalStateException::class.java) { FirebaseAuth.getInstance() }
        compose.onNodeWithTag("account_setup_required").assertIsDisplayed()
        compose.onNodeWithText("Account setup needed").assertIsDisplayed()
    }

    @Test
    fun missingFirebaseSurvivesActivityRecreation() {
        requireUnconfiguredBuild()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("account_setup_required").assertIsDisplayed()
    }
}
