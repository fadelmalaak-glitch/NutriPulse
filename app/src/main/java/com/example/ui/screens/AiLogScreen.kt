package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.MealEntry
import com.example.data.model.NutritionCalculator
import com.example.ui.viewmodel.UiState
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiLogScreen(
    initialTab: Int = 0,
    aiAnalysisState: UiState<MealEntry>,
    onAnalyzeFood: (prompt: String, bitmap: Bitmap?, logMethod: String, mealType: String) -> Unit,
    onLookupBarcode: (barcode: String, hint: String) -> Unit,
    onSaveMeal: (MealEntry) -> Unit,
    onResetAnalysis: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var selectedMealType by remember { mutableStateOf("lunch") }

    // Tab 0: Photo AI state
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoContextNote by remember { mutableStateOf("") }

    // Tab 1: Voice & Chat AI state
    var chatDescription by remember { mutableStateOf("") }
    var voiceStatusNote by remember { mutableStateOf<String?>(null) }

    // Tab 2: Barcode state
    var barcodeInput by remember { mutableStateOf("") }
    var barcodeHint by remember { mutableStateOf("") }

    // Tab 3: Manual entry state
    var manualName by remember { mutableStateOf("") }
    var manualPortion by remember { mutableStateOf("1 serving (250g)") }
    var manualCalories by remember { mutableStateOf("420") }
    var manualProtein by remember { mutableStateOf("32") }
    var manualCarbs by remember { mutableStateOf("40") }
    var manualFat by remember { mutableStateOf("14") }
    var manualFiber by remember { mutableStateOf("6") }
    var manualIngredients by remember { mutableStateOf("") }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                selectedBitmap = bmp
            } catch (e: Exception) {
                voiceStatusNote = "Could not load photo: ${e.localizedMessage}"
            }
        }
    }

    // System Camera Preview Launcher (Zero storage permission required)
    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            selectedBitmap = bmp
        }
    }

    // System Speech Recognizer Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull().orEmpty()
            if (spokenText.isNotBlank()) {
                chatDescription = spokenText
                voiceStatusNote = "Captured voice input: \"$spokenText\""
                onAnalyzeFood(spokenText, null, "voice", selectedMealType)
            }
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
                .testTag("ai_log_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Multimodal AI Food Logger",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Log meals effortlessly via Photo Vision, Voice/Chat, Open Food Facts Barcode, or Manual Entry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Meal Category Selector
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("breakfast", "lunch", "dinner", "snack").forEach { type ->
                        FilterChip(
                            selected = selectedMealType == type,
                            onClick = { selectedMealType = type },
                            label = { Text(type.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            // Mode Tabs
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Photo AI") },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Voice/Chat") },
                        icon = { Icon(Icons.Default.Mic, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Barcode") },
                        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Manual") },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                }
            }

            // Active Tab Content
            item {
                when (selectedTab) {
                    0 -> PhotoAiTabContent(
                        selectedBitmap = selectedBitmap,
                        photoContextNote = photoContextNote,
                        onPhotoContextChange = { photoContextNote = it },
                        onPickGallery = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onSnapCamera = { cameraPreviewLauncher.launch(null) },
                        onUseSampleMealPlate = {
                            selectedBitmap = createSamplePlateBitmap()
                            photoContextNote = "Grilled wild salmon fillet (160g), quinoa (120g), sliced avocado (60g), steamed edamame"
                        },
                        onAnalyzePhoto = {
                            onAnalyzeFood(
                                photoContextNote.ifBlank { "Estimate calories, macros, micronutrients, and portion weights for this meal photo." },
                                selectedBitmap,
                                "photo",
                                selectedMealType
                            )
                        }
                    )

                    1 -> VoiceChatAiTabContent(
                        chatDescription = chatDescription,
                        onDescriptionChange = { chatDescription = it },
                        voiceStatusNote = voiceStatusNote,
                        onLaunchVoice = {
                            try {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak what you ate (e.g. 2 eggs, avocado toast, oat latte)")
                                }
                                voiceLauncher.launch(intent)
                            } catch (e: Exception) {
                                voiceStatusNote = "Voice recognition dialog unavailable on this emulator; type or tap a quick meal prompt below."
                            }
                        },
                        onAnalyzeText = { method ->
                            onAnalyzeFood(chatDescription, null, method, selectedMealType)
                        }
                    )

                    2 -> BarcodeLookupTabContent(
                        barcodeInput = barcodeInput,
                        onBarcodeChange = { barcodeInput = it },
                        barcodeHint = barcodeHint,
                        onBarcodeHintChange = { barcodeHint = it },
                        onLookup = { code, hint -> onLookupBarcode(code, hint) }
                    )

                    3 -> ManualEntryTabContent(
                        foodName = manualName,
                        onFoodNameChange = { manualName = it },
                        portion = manualPortion,
                        onPortionChange = { manualPortion = it },
                        calories = manualCalories,
                        onCaloriesChange = { manualCalories = it },
                        protein = manualProtein,
                        onProteinChange = { manualProtein = it },
                        carbs = manualCarbs,
                        onCarbsChange = { manualCarbs = it },
                        fat = manualFat,
                        onFatChange = { manualFat = it },
                        fiber = manualFiber,
                        onFiberChange = { manualFiber = it },
                        ingredients = manualIngredients,
                        onIngredientsChange = { manualIngredients = it },
                        onSaveManual = {
                            val entry = MealEntry(
                                mealType = selectedMealType,
                                foodName = manualName.ifBlank { "Custom Logged Meal" },
                                portionDescription = manualPortion.ifBlank { "1 serving" },
                                portionMultiplier = 1.0,
                                calories = manualCalories.toIntOrNull() ?: 350,
                                proteinGrams = manualProtein.toDoubleOrNull() ?: 20.0,
                                carbsGrams = manualCarbs.toDoubleOrNull() ?: 35.0,
                                fatGrams = manualFat.toDoubleOrNull() ?: 12.0,
                                fiberGrams = manualFiber.toDoubleOrNull() ?: 4.0,
                                sugarGrams = 3.0,
                                sodiumMg = 320,
                                confidenceScore = 100,
                                logMethod = "manual",
                                ingredientsSummary = manualIngredients,
                                aiNotes = "Logged via verified manual entry."
                            )
                            onSaveMeal(entry)
                        }
                    )
                }
            }

            // AI Analysis Result & Portion/Ingredient Accuracy Editor Card
            item {
                when (aiAnalysisState) {
                    is UiState.Idle -> {}
                    is UiState.Loading -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Gemini AI is analyzing ingredients, portions, macros & micronutrients...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    is UiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Analysis Notice",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = aiAnalysisState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        AiResultInspectorCard(
                            analyzedMeal = aiAnalysisState.data.copy(mealType = selectedMealType),
                            onSaveConfirm = onSaveMeal,
                            onReset = onResetAnalysis
                        )
                    }

                    is UiState.Streaming -> {}
                }
            }
        }
    }
}

@Composable
private fun PhotoAiTabContent(
    selectedBitmap: Bitmap?,
    photoContextNote: String,
    onPhotoContextChange: (String) -> Unit,
    onPickGallery: () -> Unit,
    onSnapCamera: () -> Unit,
    onUseSampleMealPlate: () -> Unit,
    onAnalyzePhoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "1. Select or Capture Meal Photo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickGallery,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Photo Picker")
                }
                OutlinedButton(
                    onClick = onSnapCamera,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Camera")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            AssistChip(
                onClick = onUseSampleMealPlate,
                label = { Text("Load Culinary Sample Plate (Salmon & Quinoa Bowl)") },
                leadingIcon = { Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            if (selectedBitmap != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    bitmap = selectedBitmap.asImageBitmap(),
                    contentDescription = "Selected Meal Photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = photoContextNote,
                onValueChange = onPhotoContextChange,
                label = { Text("Optional context (e.g. cooked in 1 tbsp olive oil, half portion)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAnalyzePhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("analyze_photo_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze Meal with Gemini Vision")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceChatAiTabContent(
    chatDescription: String,
    onDescriptionChange: (String) -> Unit,
    voiceStatusNote: String?,
    onLaunchVoice: () -> Unit,
    onAnalyzeText: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = "Describe or Speak Your Meal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = onLaunchVoice) {
                    Icon(Icons.Default.Mic, contentDescription = "Speak Meal", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Voice Input")
                }
            }

            if (voiceStatusNote != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = voiceStatusNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = chatDescription,
                onValueChange = onDescriptionChange,
                label = { Text("What did you eat? (e.g. 2 poached eggs, sourdough toast, 1/2 avocado)") },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_chat_food_input")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Quick Conversational Examples:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val examples = listOf(
                    "Greek yogurt bowl (200g) with blueberries, 1 tbsp chia seeds & honey",
                    "Grilled chicken burrito bowl with brown rice, black beans, salsa & guac",
                    "Matcha oat milk latte (12oz) and a dark chocolate almond protein bar"
                )
                examples.forEach { sample ->
                    AssistChip(
                        onClick = { onDescriptionChange(sample) },
                        label = { Text(sample.take(38) + "...") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = { onAnalyzeText("chat") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("analyze_chat_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Estimate Macros & Micronutrients with AI")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarcodeLookupTabContent(
    barcodeInput: String,
    onBarcodeChange: (String) -> Unit,
    barcodeHint: String,
    onBarcodeHintChange: (String) -> Unit,
    onLookup: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Open Food Facts Live Barcode Scanner & Lookup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Queries world.openfoodfacts.org v2 REST API for verified package nutrition, with Gemini AI fallback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = barcodeInput,
                onValueChange = onBarcodeChange,
                label = { Text("EAN-13 / UPC Barcode Digits") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("barcode_input_field")
            )

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = barcodeHint,
                onValueChange = onBarcodeHintChange,
                label = { Text("Optional Brand/Product Name (for AI fallback)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Tap a real global barcode to test live Open Food Facts lookup:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val sampleBarcodes = listOf(
                    Triple("3017620422003", "Nutella Hazelnut Spread", "Nutella"),
                    Triple("737628064502", "Thai Kitchen Rice Noodles", "Rice Noodles"),
                    Triple("5449000000996", "Coca-Cola Original", "Coca-Cola"),
                    Triple("3228857000166", "Harrys 100% Whole Wheat Bread", "Whole Wheat Bread")
                )
                sampleBarcodes.forEach { (code, hint, shortLabel) ->
                    AssistChip(
                        onClick = {
                            onBarcodeChange(code)
                            onBarcodeHintChange(hint)
                            onLookup(code, hint)
                        },
                        label = { Text("$shortLabel ($code)") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = { onLookup(barcodeInput, barcodeHint) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("lookup_barcode_button")
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Lookup Barcode Nutrition")
            }
        }
    }
}

@Composable
private fun ManualEntryTabContent(
    foodName: String,
    onFoodNameChange: (String) -> Unit,
    portion: String,
    onPortionChange: (String) -> Unit,
    calories: String,
    onCaloriesChange: (String) -> Unit,
    protein: String,
    onProteinChange: (String) -> Unit,
    carbs: String,
    onCarbsChange: (String) -> Unit,
    fat: String,
    onFatChange: (String) -> Unit,
    fiber: String,
    onFiberChange: (String) -> Unit,
    ingredients: String,
    onIngredientsChange: (String) -> Unit,
    onSaveManual: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Direct Nutrition Entry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = foodName,
                onValueChange = onFoodNameChange,
                label = { Text("Meal or Food Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_food_name")
            )
            OutlinedTextField(
                value = portion,
                onValueChange = onPortionChange,
                label = { Text("Portion Description (e.g. 1 bowl, 280g)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = calories,
                    onValueChange = onCaloriesChange,
                    label = { Text("Calories (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = protein,
                    onValueChange = onProteinChange,
                    label = { Text("Protein (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = carbs,
                    onValueChange = onCarbsChange,
                    label = { Text("Carbs (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fat,
                    onValueChange = onFatChange,
                    label = { Text("Fat (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fiber,
                    onValueChange = onFiberChange,
                    label = { Text("Fiber (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = ingredients,
                onValueChange = onIngredientsChange,
                label = { Text("Ingredients / Notes") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onSaveManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_manual_meal_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Meal to Diary")
            }
        }
    }
}

@Composable
private fun AiResultInspectorCard(
    analyzedMeal: MealEntry,
    onSaveConfirm: (MealEntry) -> Unit,
    onReset: () -> Unit
) {
    var portionMultiplier by remember(analyzedMeal) { mutableFloatStateOf(1.0f) }
    var editableIngredients by remember(analyzedMeal) { mutableStateOf(analyzedMeal.ingredientsSummary) }
    var editableName by remember(analyzedMeal) { mutableStateOf(analyzedMeal.foodName) }

    val scaledMeal = remember(analyzedMeal, portionMultiplier, editableIngredients, editableName) {
        NutritionCalculator.scaleMealPortion(analyzedMeal, portionMultiplier.toDouble()).copy(
            foodName = editableName,
            ingredientsSummary = editableIngredients
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_result_inspector_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Nutritional Breakdown",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Accuracy ${scaledMeal.confidenceScore}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = editableName,
                onValueChange = { editableName = it },
                label = { Text("Detected Meal Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Portion Multiplier: ${(portionMultiplier * 100).roundToInt() / 100.0}x (${scaledMeal.portionDescription})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = portionMultiplier,
                onValueChange = { portionMultiplier = it },
                valueRange = 0.25f..3.0f,
                steps = 10
            )

            // Live Scaled Macros Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MicronutrientStat("Calories", "${scaledMeal.calories} kcal")
                MicronutrientStat("Protein", "${scaledMeal.proteinGrams.roundToInt()}g")
                MicronutrientStat("Carbs", "${scaledMeal.carbsGrams.roundToInt()}g")
                MicronutrientStat("Fat", "${scaledMeal.fatGrams.roundToInt()}g")
                MicronutrientStat("Fiber", "${scaledMeal.fiberGrams.roundToInt()}g")
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = editableIngredients,
                onValueChange = { editableIngredients = it },
                label = { Text("Ingredient Breakdown (Edit to refine)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (scaledMeal.aiNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Micronutrient & Dietitian Insight: ${scaledMeal.aiNotes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Discard")
                }
                Button(
                    onClick = { onSaveConfirm(scaledMeal) },
                    modifier = Modifier
                        .weight(2f)
                        .testTag("confirm_save_ai_meal_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to Daily Diary")
                }
            }
        }
    }
}

/**
 * Generates an on-device culinary sample bitmap (Salmon, Quinoa, Avocado, Edamame bowl)
 * so users on emulators without camera photos can immediately test multimodal Gemini Vision.
 */
private fun createSamplePlateBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Slate table background
    paint.color = AndroidColor.parseColor("#1E293B")
    canvas.drawRect(0f, 0f, 480f, 320f, paint)

    // Ceramic bowl
    paint.color = AndroidColor.parseColor("#F8FAFC")
    canvas.drawCircle(240f, 160f, 130f, paint)

    // Quinoa base
    paint.color = AndroidColor.parseColor("#FDE68A")
    canvas.drawCircle(210f, 180f, 65f, paint)

    // Grilled salmon fillet
    paint.color = AndroidColor.parseColor("#FB7185")
    canvas.drawRoundRect(210f, 80f, 330f, 165f, 24f, 24f, paint)

    // Avocado slices
    paint.color = AndroidColor.parseColor("#4ADE80")
    canvas.drawCircle(165f, 125f, 42f, paint)

    // Edamame beans
    paint.color = AndroidColor.parseColor("#10B981")
    canvas.drawCircle(285f, 210f, 35f, paint)

    return bmp
}
