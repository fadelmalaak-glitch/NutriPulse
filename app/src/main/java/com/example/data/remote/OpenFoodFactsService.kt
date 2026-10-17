package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class BarcodeProductResult(
    val barcode: String,
    val productName: String,
    val brand: String,
    val servingSize: String,
    val calories: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double,
    val sugarGrams: Double,
    val sodiumMg: Int,
    val ingredientsText: String
)

object OpenFoodFactsClient {
    private const val TAG = "OpenFoodFactsClient"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Queries the live Open Food Facts v2 REST API for a scanned or entered barcode.
     */
    suspend fun lookupBarcode(barcode: String): Result<BarcodeProductResult> = withContext(Dispatchers.IO) {
        val cleanBarcode = barcode.trim().filter { it.isDigit() }
        if (cleanBarcode.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid barcode (at least 6 digits)."))
        }

        val url = "https://world.openfoodfacts.org/api/v2/product/$cleanBarcode.json"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NutriPulse-Android/1.0 (https://aistudio.google.com)")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Product not found in Open Food Facts (HTTP ${response.code}).")
                    )
                }
                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Empty response from Open Food Facts."))
                }

                val root = JSONObject(bodyStr)
                val status = root.optInt("status", 0)
                if (status != 1 || !root.has("product")) {
                    return@withContext Result.failure(
                        NoSuchElementException("Barcode $cleanBarcode was not found in the Open Food Facts database.")
                    )
                }

                val product = root.getJSONObject("product")
                val name = product.optString("product_name_en")
                    .ifBlank { product.optString("product_name") }
                    .ifBlank { "Scanned Food Product" }
                val brands = product.optString("brands", "")
                val servingSize = product.optString("serving_size", "100g")
                val ingredients = product.optString("ingredients_text_en")
                    .ifBlank { product.optString("ingredients_text", "Standard packaged ingredients") }

                val nutriments = product.optJSONObject("nutriments") ?: JSONObject()

                val kcal = nutriments.optDouble("energy-kcal_serving", Double.NaN)
                    .let { if (it.isNaN()) nutriments.optDouble("energy-kcal_100g", 0.0) else it }
                    .roundToInt()
                    .coerceIn(0, 5000)

                val protein = nutriments.optDouble("proteins_serving", Double.NaN)
                    .let { if (it.isNaN()) nutriments.optDouble("proteins_100g", 0.0) else it }
                    .let { (it * 10.0).roundToInt() / 10.0 }
                    .coerceIn(0.0, 500.0)

                val carbs = nutriments.optDouble("carbohydrates_serving", Double.NaN)
                    .let { if (it.isNaN()) nutriments.optDouble("carbohydrates_100g", 0.0) else it }
                    .let { (it * 10.0).roundToInt() / 10.0 }
                    .coerceIn(0.0, 1000.0)

                val fat = nutriments.optDouble("fat_serving", Double.NaN)
                    .let { if (it.isNaN()) nutriments.optDouble("fat_100g", 0.0) else it }
                    .let { (it * 10.0).roundToInt() / 10.0 }
                    .coerceIn(0.0, 500.0)

                val fiber = nutriments.optDouble("fiber_serving", Double.NaN)
                    .let { if (it.isNaN()) nutriments.optDouble("fiber_100g", 0.0) else it }
                    .let { (it * 10.0).roundToInt() / 10.0 }
                    .coerceIn(0.0, 200.0)

                val sugar = nutriments.optDouble("sugars_serving", Double.NaN)
                    .let { if (it.isNaN()) nutriments.optDouble("sugars_100g", 0.0) else it }
                    .let { (it * 10.0).roundToInt() / 10.0 }
                    .coerceIn(0.0, 500.0)

                val sodiumGrams = nutriments.optDouble("sodium_serving", Double.NaN)
                    .let { if (it.isNaN()) nutriments.optDouble("sodium_100g", 0.0) else it }
                val sodiumMg = (sodiumGrams * 1000.0).roundToInt().coerceIn(0, 20000)

                val displayTitle = if (brands.isNotBlank() && !name.contains(brands, ignoreCase = true)) {
                    "$brands $name"
                } else {
                    name
                }

                Result.success(
                    BarcodeProductResult(
                        barcode = cleanBarcode,
                        productName = displayTitle.take(140),
                        brand = brands.take(60),
                        servingSize = servingSize.take(60).ifBlank { "100g" },
                        calories = kcal,
                        proteinGrams = protein,
                        carbsGrams = carbs,
                        fatGrams = fat,
                        fiberGrams = fiber,
                        sugarGrams = sugar,
                        sodiumMg = sodiumMg,
                        ingredientsText = ingredients.take(450)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenFoodFacts barcode lookup failed for $cleanBarcode", e)
            Result.failure(e)
        }
    }
}
