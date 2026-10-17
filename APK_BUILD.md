# Build and download the Android APK

1. Open [Build Android APK](https://github.com/fadelmalaak-glitch/NutriPulse/actions/workflows/main.yml).
2. Open the latest successful run. To build manually, choose **Run workflow** on the **main** branch.
3. Under **Artifacts**, download **NutriPulse-debug-apk** while signed in to GitHub.
4. Extract the ZIP and install **app-debug.apk** on an Android 7.0+ device.

Artifacts are retained for 30 days. Run the workflow again if a download has expired.
The run summary also contains a direct artifact link.

## Build configuration

The workflow uses Java 17, Gradle 9.3.1, Android API 36.1 and build tools 36.0.0.
The source export has no Gradle launcher or wrapper JAR, so CI installs Gradle explicitly.
It creates the root debug.keystore expected by the existing signing configuration and builds
with `gradle :app:assembleDebug --no-daemon --stacktrace --console=plain`.

This is a debug build for testing. Each run generates a new debug signing certificate;
it cannot update an installed APK signed by a different certificate.
Production releases should use a persistent, securely managed release signing key.
The release signing configuration is unchanged.

## Firebase configuration

The export does not include app/google-services.json. A debug APK can be compiled without it,
but Firebase authentication, cloud storage and AI features require the real Firebase project configuration.

In Firebase Console, select the intended project and register/select the Android app with package
`com.aistudio.nutripulse.nvrkpx`. Download its google-services.json.
In this repository's **Settings > Secrets and variables > Actions**, add the complete JSON file
as the repository secret **GOOGLE_SERVICES_JSON**, then run the workflow again.
Do not use a service-account private key for this secret.

Google Sign-In and App Check may also require registering the signing certificate fingerprints
and configuring the appropriate providers in Firebase. A stable signing key is needed for
consistent device authentication across builds.
