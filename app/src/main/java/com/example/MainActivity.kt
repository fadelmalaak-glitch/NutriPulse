package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.UserProfile
import com.example.data.repository.NutriPulseRepository
import com.example.ui.screens.AiLogScreen
import com.example.ui.screens.AnalyticsAndGoalsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CoachScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.PlannerAndGroceryScreen
import com.example.ui.theme.NutriPulseTheme
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.viewmodel.NutriPulseViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.AuthAccountInfo
import com.example.util.AuthMode
import com.example.util.FirebaseAuthService
import com.example.util.SignUpPreferences
import com.example.util.configureAppCheck
import com.example.util.signOut
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkThemeOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
            val useDarkTheme = darkThemeOverride ?: systemDark

            NutriPulseTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        isDarkTheme = useDarkTheme,
                        onToggleDarkTheme = { darkThemeOverride = it }
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    isDarkTheme: Boolean = false,
    onToggleDarkTheme: (Boolean) -> Unit = {}
) {
    val authService = remember(auth) { FirebaseAuthService(auth) }
    val currentUser by authService.observeAuthState().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var lastAuthAccountInfo by remember { mutableStateOf<AuthAccountInfo?>(null) }
    var lastSignUpPreferences by remember { mutableStateOf(SignUpPreferences()) }
    val user = currentUser

    if (user == null) {
        AuthScreen(
            authService = authService,
            onAuthSuccess = { accountInfo, signUpPrefs ->
                lastAuthAccountInfo = accountInfo
                lastSignUpPreferences = signUpPrefs
            }
        )
    } else {
        val displayName = lastAuthAccountInfo?.displayName
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "NutriPulse Member"
        MainFeatureScreen(
            currentUserId = user.uid,
            accountDisplayName = displayName,
            accountEmail = lastAuthAccountInfo?.email ?: user.email,
            signUpPreferences = lastSignUpPreferences,
            isSignUpSession = lastAuthAccountInfo?.authMode == AuthMode.SIGN_UP || lastAuthAccountInfo?.isNewUser == true,
            isDarkTheme = isDarkTheme,
            onToggleDarkTheme = onToggleDarkTheme,
            authService = authService
        )
    }
}

private enum class NutriDestination(val label: String, val icon: ImageVector, val tag: String) {
    DIARY("Diary", Icons.Default.Dashboard, "nav_tab_diary"),
    AI_LOG("AI Log", Icons.Default.AddCircle, "nav_tab_ai_log"),
    COACH("AI Coach", Icons.Default.AutoAwesome, "nav_tab_coach"),
    PLANNER("Planner", Icons.Default.RestaurantMenu, "nav_tab_planner"),
    ANALYTICS("Analytics", Icons.Default.Insights, "nav_tab_analytics")
}

@Composable
fun MainFeatureScreen(
    currentUserId: String,
    accountDisplayName: String = "NutriPulse Member",
    accountEmail: String? = null,
    signUpPreferences: SignUpPreferences = SignUpPreferences(),
    isSignUpSession: Boolean = false,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    authService: FirebaseAuthService = remember { FirebaseAuthService() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: NutriPulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NutriPulseViewModel(NutriPulseRepository(db), currentUserId)
            }
        }
    )

    LaunchedEffect(currentUserId, accountDisplayName, signUpPreferences, isSignUpSession) {
        viewModel.initializeAccountProfile(
            displayName = accountDisplayName,
            goalType = signUpPreferences.goalType,
            dietaryPreference = signUpPreferences.dietaryPreference,
            activityLevel = signUpPreferences.activityLevel,
            isSignUp = isSignUpSession
        )
    }

    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()
    val summary by viewModel.dailySummary.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val mealsState by viewModel.mealsState.collectAsStateWithLifecycle()
    val waterLogsState by viewModel.waterLogsState.collectAsStateWithLifecycle()
    val weightLogsState by viewModel.weightLogsState.collectAsStateWithLifecycle()
    val mealPlansState by viewModel.mealPlansState.collectAsStateWithLifecycle()
    val groceryItemsState by viewModel.groceryItemsState.collectAsStateWithLifecycle()
    val aiFoodAnalysisState by viewModel.aiFoodAnalysisState.collectAsStateWithLifecycle()
    val mealPlanGenState by viewModel.mealPlanGenerationState.collectAsStateWithLifecycle()
    val coachMessages by viewModel.coachMessages.collectAsStateWithLifecycle()
    val coachUiState by viewModel.coachUiState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data ?: UserProfile(userId = currentUserId)
    val meals = (mealsState as? UiState.Success)?.data ?: emptyList()
    val waterLogs = (waterLogsState as? UiState.Success)?.data ?: emptyList()
    val weightLogs = (weightLogsState as? UiState.Success)?.data ?: emptyList()
    val mealPlans = (mealPlansState as? UiState.Success)?.data ?: emptyList()
    val groceryItems = (groceryItemsState as? UiState.Success)?.data ?: emptyList()

    var currentTab by rememberSaveable { mutableStateOf(NutriDestination.DIARY) }
    var aiLoggerInitialTab by rememberSaveable { mutableIntStateOf(0) }

    // Mandatory BackHandler for secondary screens returning to DIARY home screen
    if (currentTab != NutriDestination.DIARY) {
        BackHandler {
            currentTab = NutriDestination.DIARY
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        NutriDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = currentTab == dest,
                                onClick = { currentTab = dest },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail {
                        NutriDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = currentTab == dest,
                                onClick = { currentTab = dest },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    AnimatedVisibility(visible = statusBanner != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = statusBanner.orEmpty(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.dismissStatusBanner() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            NutriDestination.DIARY -> DashboardScreen(
                                selectedDateKey = selectedDateKey,
                                onSelectDateKey = { viewModel.selectDateKey(it) },
                                summary = summary,
                                meals = meals,
                                waterLogs = waterLogs,
                                onOpenAiLogger = { subTab ->
                                    aiLoggerInitialTab = subTab
                                    currentTab = NutriDestination.AI_LOG
                                },
                                onScaleMealPortion = { meal, mult -> viewModel.scaleMealPortion(meal, mult) },
                                onUpdateMeal = { updated -> viewModel.saveMealEntry(updated) },
                                onDeleteMeal = { id -> viewModel.deleteMealEntry(id) },
                                onLogWater = { ml, bev -> viewModel.logWater(ml, bev) },
                                onDeleteWater = { id -> viewModel.deleteWaterLog(id) }
                            )

                            NutriDestination.AI_LOG -> AiLogScreen(
                                initialTab = aiLoggerInitialTab,
                                aiAnalysisState = aiFoodAnalysisState,
                                onAnalyzeFood = { prompt, bmp, method, mealType ->
                                    viewModel.analyzeFoodWithAi(prompt, bmp, method, mealType)
                                },
                                onLookupBarcode = { code, hint ->
                                    viewModel.lookupBarcode(code, hint)
                                },
                                onSaveMeal = { meal ->
                                    viewModel.saveMealEntry(meal) {
                                        currentTab = NutriDestination.DIARY
                                    }
                                },
                                onResetAnalysis = { viewModel.resetAiFoodAnalysis() }
                            )

                            NutriDestination.COACH -> CoachScreen(
                                summary = summary,
                                messages = coachMessages,
                                coachState = coachUiState,
                                onSendMessage = { viewModel.askNutritionCoach(it) }
                            )

                            NutriDestination.PLANNER -> PlannerAndGroceryScreen(
                                profile = profile,
                                mealPlans = mealPlans,
                                groceryItems = groceryItems,
                                mealPlanGenState = mealPlanGenState,
                                onGenerateAiMealPlan = { day, note -> viewModel.generateAiMealPlan(day, note) },
                                onLogPlannedMeal = { viewModel.logPlannedMealToDiary(it) },
                                onDeleteMealPlan = { viewModel.deleteMealPlanItem(it) },
                                onAddGroceryItem = { name, qty, cat -> viewModel.addGroceryItem(name, qty, cat) },
                                onToggleGroceryItem = { viewModel.toggleGroceryItem(it) },
                                onDeleteGroceryItem = { viewModel.deleteGroceryItem(it) }
                            )

                            NutriDestination.ANALYTICS -> AnalyticsAndGoalsScreen(
                                profile = profile,
                                accountEmail = accountEmail,
                                meals = meals,
                                weightLogs = weightLogs,
                                isDarkTheme = isDarkTheme,
                                onToggleDarkTheme = onToggleDarkTheme,
                                onUpdateProfileGoals = { name, age, gender, h, cw, tw, act, goal, diet ->
                                    viewModel.updatePersonalizedProfile(name, age, gender, h, cw, tw, act, goal, diet)
                                },
                                onLogWeight = { w, bf, note -> viewModel.logWeightCheckIn(w, bf, note) },
                                onSignOut = {
                                    signOut(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onSignOutComplete = {},
                                        scope = coroutineScope,
                                        authService = authService
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
