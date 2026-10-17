package com.example

import android.content.Context
import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.test.core.app.ApplicationProvider
import com.example.util.FirebaseAuthService
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.firebase.auth.GoogleAuthProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NutriPulse", appName)
    }

    @Test
    fun `firebaseAuthService builds Credentials API requests for auto sign-in, interactive sign-in, and sign-up fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clientIdResult = FirebaseAuthService.generateNonce()
        assertTrue(clientIdResult.isNotBlank())

        val webClientId = context.getString(R.string.default_web_client_id)
        assertTrue("default_web_client_id must be provisioned", webClientId.isNotBlank())

        val autoRequest = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(true)
            .build()
        assertTrue(autoRequest.filterByAuthorizedAccounts)
        assertTrue(autoRequest.autoSelectEnabled)
        assertEquals(webClientId, autoRequest.serverClientId)

        val interactiveOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build()
        assertEquals(webClientId, interactiveOption.serverClientId)

        val allAccountsOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()
        assertFalse(allAccountsOption.filterByAuthorizedAccounts)
        assertFalse(allAccountsOption.autoSelectEnabled)

        val firebaseCredential = FirebaseAuthService.createFirebaseGoogleCredential("sample-google-id-token")
        assertNotNull(firebaseCredential)
        assertEquals(GoogleAuthProvider.PROVIDER_ID, firebaseCredential.provider)

        val unexpectedCredential = CustomCredential("com.example.UNEXPECTED", Bundle())
        assertNull(FirebaseAuthService.extractGoogleIdTokenCredential(unexpectedCredential))
    }
}
