# NutriPulse — Google Play Store Publishing & Privacy Policy Guide

## 1. Google Play Store Listing Metadata
- **App Name (<= 30 chars):** `NutriPulse`
- **Package Name (`applicationId`):** `com.aistudio.nutripulse.nvrkpx`
- **Category:** Health & Fitness
- **Short Description (<= 80 chars):**
  `AI calorie tracker, photo & voice food journal, nutrition coach & meal planner.`
- **Full Description:**
  NutriPulse is an intelligent calorie tracking and personalized nutrition journal designed to make healthy eating effortless.
  - **Multimodal AI Food Logging:** Snap a meal photo, speak naturally via voice input, chat with AI, scan packaged food barcodes via Open Food Facts, or log manually.
  - **Transparent AI Accuracy & Portion Control:** View nutritional estimation confidence scores, scale portions from 0.25x to 3.0x in one tap, and refine detected ingredients.
  - **Personalized Mifflin-St Jeor Targets:** Calculate custom daily calorie, protein, carbohydrate, fat, dietary fiber, and hydration goals tailored to your body metrics, activity level, and dietary style (Balanced, High Protein, Mediterranean, Plant-Based, Low Carb, Keto).
  - **Live Streaming AI Nutrition Coach:** Get real-time meal recommendations and macro adjustments based on what you've eaten today.
  - **Meal Planner, Recipes & Smart Grocery List:** Generate full-day meal plans calibrated to your macro targets and sync ingredients directly to an aisle-sorted grocery checklist.
  - **Analytics & Progress Tracking:** Visualize 7-day calorie adherence and weight/body-fat trajectory with encrypted per-user Cloud Firestore synchronization.

## 2. Privacy Policy & Google Play Data Safety Declarations
Include the following disclosures in your hosted Privacy Policy URL and Google Play Console **Data Safety** section:
- **Authentication & Account Data:** Uses Google Sign-In via Android Jetpack Credential Manager (`GetSignInWithGoogleOption`). User IDs (`uid`) are used strictly to isolate user records under `/users/{userId}` in Cloud Firestore.
- **Health & Nutrition Data:** Logged meals, hydration entries, weight check-ins, and personalized macro targets are stored in Cloud Firestore protected by zero-trust Attribute-Based Access Control (ABAC) security rules (`request.auth.uid == userId`). Users can edit or delete any logged record at any time inside the app.
- **Photos & Voice Input:** Meal photos selected via the zero-permission Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) and voice-to-text descriptions are sent over TLS to Firebase AI Logic (Gemini API) solely to estimate nutritional content and are not shared with third-party advertisers.
- **Permissions Used:**
  - `INTERNET` & `ACCESS_NETWORK_STATE`: Cloud Firestore sync, Firebase AI Logic inference, and Open Food Facts API barcode lookup.
  - `POST_NOTIFICATIONS`: Optional user-controlled smart meal and hydration reminders.
  - Zero broad storage permissions (`READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES` are NOT requested).

## 3. Generating & Publishing the Release Android App Bundle (`.aab`)
1. **Export or Build Signed AAB:**
   - In Google AI Studio, open the **Settings / Export** menu to generate a signed APK/AAB or export the project ZIP.
   - Locally with Android Studio or Gradle CLI, configure your upload keystore (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) and run:
     `gradle :app:bundleRelease`
2. **Google Play Console Setup:**
   - Create a new app in [Google Play Console](https://play.google.com/console) with default language `en-US`.
   - Complete the **App Content** declarations (Privacy Policy URL, Data Safety, Health Apps declaration, Target Audience 13+).
   - Upload the generated `.aab` file to **Testing > Internal testing** or **Production**, attach release notes for `1.0.0 (versionCode 1)`, and submit for review.
