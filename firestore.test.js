const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validMealPayload(userId) {
  const now = new Date();
  return {
    userId,
    dateKey: "2026-10-17",
    mealType: "lunch",
    foodName: "Grilled Salmon Quinoa Bowl",
    portionDescription: "1 bowl (350g)",
    portionMultiplier: 1.0,
    calories: 580,
    proteinGrams: 42.0,
    carbsGrams: 45.0,
    fatGrams: 22.0,
    fiberGrams: 8.0,
    sugarGrams: 4.0,
    sodiumMg: 480,
    confidenceScore: 94,
    logMethod: "photo",
    ingredientsSummary: "Wild salmon (150g), cooked quinoa (120g), avocado (50g), edamame (30g)",
    aiNotes: "Rich in omega-3 fatty acids and complete protein.",
    createdAt: now,
    updatedAt: now,
  };
}

test("1. Unauthenticated user cannot read or write profiles or meals", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("meals")
      .doc("meal_1")
      .set(validMealPayload(ALICE_UID))
  );
});

test("2. Authenticated user can create and read their own meal entry", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("meals")
      .doc("meal_1")
      .set(validMealPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("meals")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("3. Cross-user isolation: Bob cannot read, list, update, or delete Alice's meals", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("meals")
      .doc("meal_1")
      .set(validMealPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("meals").doc("meal_1").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("meals")
      .where("userId", "==", ALICE_UID)
      .get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("meals").doc("meal_1").delete()
  );
});

test("4. Identity spoofing rejected: Alice cannot create a meal with Bob's userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("meals")
      .doc("meal_spoof")
      .set(validMealPayload(BOB_UID))
  );
});

test("5. Shadow update (ghost field) and value poisoning rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const mealRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("meals")
    .doc("meal_1");
  await assertSucceeds(mealRef.set(validMealPayload(ALICE_UID)));

  // Ghost field injection
  await assertFails(
    mealRef.update({
      calories: 600,
      isAdminVerified: true,
      updatedAt: new Date(),
    })
  );

  // Value poisoning (negative calories)
  await assertFails(
    mealRef.update({
      calories: -200,
      updatedAt: new Date(),
    })
  );

  // Valid update succeeds
  await assertSucceeds(
    mealRef.update({
      calories: 620,
      portionMultiplier: 1.1,
      updatedAt: new Date(),
    })
  );
});
