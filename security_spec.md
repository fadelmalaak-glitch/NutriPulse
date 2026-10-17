# NutriPulse Security Specification (Phase 0 TDD)

## 1. Data Invariants
1. **Multi-User Isolation**: Every document under `/users/{userId}` and its subcollections (`meals`, `water_logs`, `weight_logs`, `meal_plans`, `grocery_items`) belongs exclusively to `userId` (`request.auth.uid == userId` and `data.userId == request.auth.uid`).
2. **Immutable Ownership & Creation Time**: `userId` and `createdAt` can never be mutated during an `update` operation (`incoming().userId == existing().userId && incoming().createdAt == existing().createdAt`).
3. **Query Enforcer**: Every `allow list` operation requires `resource.data.userId == request.auth.uid` so un-filtered queries or cross-user queries are rejected.
4. **Strict Key & Bounds Validation**: Every `create` and `update` invokes `isValid[Entity](incoming())` and enforces `.keys().hasOnly(...)` / `.diff(existing()).affectedKeys().hasOnly(...)` and string `.size()` bounds.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Profile Read**: `unauthDb.doc("users/alice_123").get()` -> Reject (`PERMISSION_DENIED`).
2. **Cross-User Profile Read**: `bobDb.doc("users/alice_123").get()` -> Reject (`PERMISSION_DENIED`).
3. **Identity Spoofing on Meal Create**: Alice creates `/users/alice_123/meals/meal_1` with `userId: "bob_456"` -> Reject.
4. **Shadow/Ghost Field Injection on Meal Update**: Alice updates `/users/alice_123/meals/meal_1` adding `isVerifiedAdmin: true` -> Reject via `affectedKeys().hasOnly(...)`.
5. **Immutable `createdAt` Tampering**: Alice updates `createdAt` on an existing `MealEntry` -> Reject (`incoming().createdAt == existing().createdAt`).
6. **Immutable `userId` Transfer**: Alice updates `userId` on an existing `MealEntry` to `"bob_456"` -> Reject.
7. **Unfiltered List Query (Query Trust Test)**: Alice queries `users/alice_123/meals` without `.where("userId", "==", "alice_123")` or queries Bob's collection -> Reject.
8. **Negative Calorie / Out-of-Range Macro Value Poisoning**: Alice creates a `MealEntry` with `calories: -500` or `calories: 999999` -> Reject via `isValidMealEntry`.
9. **Oversized String DoS ("Denial of Wallet")**: Alice creates a `MealEntry` with `foodName` of 5,000 chars -> Reject (`foodName.size() <= 150`).
10. **Invalid Enum Injection**: Alice creates a `MealEntry` with `mealType: "midnight_feast"` -> Reject.
11. **Future Timestamp Spoofing**: Alice creates a `WaterLog` with `createdAt` in the future (`> request.time`) -> Reject.
12. **Path ID Poisoning**: Alice creates a document with invalid characters in ID (`meal$bad!id`) -> Reject via `isValidId()`.
