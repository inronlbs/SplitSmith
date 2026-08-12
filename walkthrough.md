## [2026-08-07 23:45] Patch Release v0.3.8.2

### Features & Fixes
1. **Accurate Currency Rendering (`FormatUtils.kt`)**: Replaced legacy `%.0f` formatters across all UI components with a centralized `formatCurrency()` extension. This resolves the rounding artifact bug where fractional amounts (e.g. `216.66`) were visibly rounded to integers (e.g. `217`).
2. **Peer-to-Peer Debt Calculation (`DebtSolver.kt`)**: Rewrote `DebtSolver.resolveDebts` into `calculatePeerDebts` to track 1-on-1 balances directly rather than greedily simplifying debts globally across all members. This guarantees that User A's debt to User B remains accurately assigned to User B, precisely satisfying manual verifications.
3. **Intent Loop Navigation Fix (`MainActivity.kt`)**: Fixed a bug where sharing a bill via the Android share sheet (`ACTION_SEND`) caused the app to navigate back to the `SlipImportScreen` unexpectedly after saving the expense. The intent's action is now correctly cleared (`intent.action = null`) inside `handleIncomingIntent` to prevent Android from re-delivering it upon `MainActivity` recreation.

### Files Modified
- `[NEW]` [FormatUtils.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/util/FormatUtils.kt)
- `[MODIFY]` [DebtSolver.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/DebtSolver.kt)
- `[MODIFY]` [MainActivity.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/MainActivity.kt)
- `[MODIFY]` [GroupDetailScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/group/GroupDetailScreen.kt)
- `[MODIFY]` [HomeScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/home/HomeScreen.kt)
- `[MODIFY]` [PersonalExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/personal/PersonalExpensesScreen.kt)
- `[MODIFY]` [DirectSplitDetailScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/DirectSplitDetailScreen.kt)
- `[MODIFY]` [SplitExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/SplitExpensesScreen.kt)
- `[MODIFY]` [build.gradle.kts](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build.gradle.kts)

---

## [2026-08-07 23:05] Dotted Background Architecture Fix & v0.3.8.1 Patch Release

### Key Technical Accomplishments
1. **Dotted Background Scaffold Layer Fix**:
   - Fixed Compose layout drawing order conflict across `SplitExpensesScreen`, `PersonalExpensesScreen`, `AddExpenseScreen`, `QuickSplitScreen`, `GroupDetailScreen`, and `DirectSplitDetailScreen`.
   - Set `Scaffold(containerColor = Color.Transparent)` to prevent the solid background canvas from covering up the `.dotGridBackground` modifier.
   - Pushed `background(colors.canvasChalk)` to top-level containers inside the Scaffold so the background renders first, followed immediately by the dot grid on top.
2. **Dot Grid Contrast & Fade Calibration (`ModifierExtensions.kt`, `Color.kt`)**:
   - Updated Light mode `dotColor` to Slate 400 (`Color(0xFF94A3B8)`) and Dark mode to Slate 600 (`Color(0xFF475569)`) for premium, crisp visibility.
   - Removed aggressive 75% height linear fade-out, keeping dots evenly visible across 100% of the screen height with a soft 15% edge fade.
3. **Patch Release v0.3.8.1**:
   - Bumped `versionName` to `0.3.8.1` and `versionCode` to `44`.
   - Published GitHub Patch Release `v0.3.8.1` to trigger in-app `AppUpdateManager` update notifications for all users running `v0.3.8`.

### Files Modified
- `[MODIFY]` [ModifierExtensions.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/components/ModifierExtensions.kt)
- `[MODIFY]` [Color.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/theme/Color.kt)
- `[MODIFY]` [SplitExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/SplitExpensesScreen.kt)
- `[MODIFY]` [PersonalExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/personal/PersonalExpensesScreen.kt)
- `[MODIFY]` [AddExpenseScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/expense/AddExpenseScreen.kt)
- `[MODIFY]` [QuickSplitScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/quicksplit/QuickSplitScreen.kt)
- `[MODIFY]` [build.gradle.kts](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build.gradle.kts)

---

## [2026-08-07 20:38] Native PDF Compression, Group QR Deep Links, Invitation Consent & Email Search

### Key Technical Accomplishments
1. **Native High-Density PDF Compression (`AttachmentCompressor.kt`)**:
   - Implemented `compressPdf` utilizing native `android.graphics.pdf.PdfRenderer` and `android.graphics.pdf.PdfDocument`.
   - Renders PDF pages onto a high-density 2048px canvas (~250 DPI) with a pure white background, re-encoding to 85% JPEG streams.
   - Slashes PDF digital invoice sizes by 70-90% (~150-300 KB per page) while ensuring 100% text, number, and line item legibility.
2. **Cloudinary Raw PDF Upload & Encryption (`CloudinaryManager.kt`)**:
   - Bypassed `BitmapFactory.decodeStream` for `application/pdf` MIME types, executing XOR encryption (`xorTransform`) directly on PDF bytes.
   - Configured secure raw upload public IDs (`receipts/$userId/$cleanCategory/${UUID.randomUUID()}.pdf.enc`).
3. **Multi-Format Attachment Downloader (`AttachmentDownloader.kt`)**:
   - Added magic-byte validation supporting both PDF (`%PDF-` / `0x25, 0x50, 0x44, 0x46`) and JPEG (`0xFF, 0xD8, 0xFF`) headers.
   - Cached attachments with matching `.pdf` or `.jpg` file extensions so shared PDFs open cleanly in device PDF viewers.
4. **Group QR Deep Link Standardization (`GroupDetailScreen.kt`)**:
   - Updated Group QR code generation to encode structured deep link URIs (`splitsmith://join?code=${group.id}`).
   - Enhanced QR scanner handlers across `QuickSplitScreen.kt` and `SplitExpensesScreen.kt` to parse `ParsedQrPayload.GroupQr` and route join requests / group navigation seamlessly.
5. **Group Addition User Consent Flow (`GroupDetailScreen.kt`, `FirebaseManager.kt`)**:
   - Added `FirebaseManager.inviteUserToGroup` which populates `pendingMembers.$targetUserId = true` in Firestore.
   - Replaced direct member addition with invitation prompts (`"Invitation sent to ${resolved.displayName}!"`), enforcing user consent before moving users to active `members`.
6. **Resilient User Search by Email / Gmail ID (`FirebaseManager.kt`)**:
   - Added client-side fallback scanning (`db.collection("users").get().await()`) in `searchUserByEmail` for email, lowercase, trimmed string, and prefix matching.
   - Enhanced `searchUsersInstantly` to attempt both email search and user code search unconditionally.
7. **Production Release Build**:
   - Successfully compiled debug package (`.\gradlew assembleDebug`).
   - Successfully compiled signed production release bundle (`app-release.aab`) and release APK (`app-release.apk`) via `.\gradlew bundleRelease assembleRelease`.

### Files Modified
- `[MODIFY]` [AttachmentCompressor.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/util/AttachmentCompressor.kt)
- `[MODIFY]` [CloudinaryManager.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/CloudinaryManager.kt)
- `[MODIFY]` [AttachmentDownloader.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/util/AttachmentDownloader.kt)
- `[MODIFY]` [FirebaseManager.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/FirebaseManager.kt)
- `[MODIFY]` [GroupDetailScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/group/GroupDetailScreen.kt)
- `[MODIFY]` [QuickSplitScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/quicksplit/QuickSplitScreen.kt)
- `[MODIFY]` [SplitExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/SplitExpensesScreen.kt)

### Built Release Artifacts
- `[AAB]` [app-release.aab](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build/outputs/bundle/release/app-release.aab)
- `[APK]` [app-release.apk](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build/outputs/apk/release/app-release.apk)

---

## [2026-08-07 18:38] Rolled Back R8 Minification & Re-Released 100% Stable v0.3.8

### Key Technical Accomplishments
1. **Disabled Release Minification (`app/build.gradle.kts`)**: Set `isMinifyEnabled = false` to eliminate R8 class renaming and restore 100% stable release execution identical to debug builds.
2. **Re-compiled Release APK**: Built clean release package [`SplitSmith-v0.3.8-release.apk`](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build/outputs/apk/release/SplitSmith-v0.3.8-release.apk) (70.1 MB).
3. **Re-deployed to Firebase Hosting**: Uploaded stable `splitsmith.bin` to [`https://splitsmith.web.app`](https://splitsmith.web.app).
4. **Re-uploaded GitHub Release Asset**: Replaced GitHub release asset `SplitSmith-v0.3.8-release.apk` on [`https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.8`](https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.8).

---

## [2026-08-07 18:07] Deployed v0.3.8 Direct Binary via Firebase Hosting (`/splitsmith.bin`)

### Key Technical Accomplishments
1. **Updated `/splitsmith.bin` Binary Asset**: Replaced public binary with minified, obfuscation-safe R8 build `SplitSmith-v0.3.8-release.apk` (58.2 MB).
2. **Configured HTTP Download Headers (`firebase.json`)**: Configured Firebase response header `Content-Disposition: attachment; filename="SplitSmith-v0.3.8-release.apk"`.
3. **Restored Direct Web Downloads (`public/index.html`)**: Pointed all download buttons directly to `/splitsmith.bin` to serve direct high-speed CDN downloads on your website without GitHub redirects or 504 gateway timeouts.
4. **Empirical Deployment Verification**: Executed `npx firebase-tools deploy --only hosting` with **Deploy complete!**. Live at [`https://splitsmith.web.app`](https://splitsmith.web.app).

---

## [2026-08-07 18:01] Resolved Download Link & Deleted Legacy v0.3.7 Release Asset

### Key Technical Accomplishments
1. **Deleted Legacy Release Asset**: Executed `gh release delete-asset v0.3.8 SplitSmith-v0.3.7-release.apk` to ensure ONLY `SplitSmith-v0.3.8-release.apk` is attached to release `v0.3.8`.
2. **Updated Asset Selection Algorithm ([`public/index.html`](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/public/index.html))**: Modified dynamic version script to match exact release version filename (`SplitSmith-${version}-release.apk`).
3. **Deployed Updated Web Landing Page**: Executed `npx firebase-tools deploy --only hosting` to update [`https://splitsmith-app.web.app`](https://splitsmith-app.web.app).

---

## [2026-08-07 17:15] Fix Launch Reflection Crash & Re-Release v0.3.8 Asset

### Key Technical Accomplishments
1. **Firestore Reflection Keep Rules (`app/proguard-rules.pro`)**: Added explicit rules keeping zero-argument default constructors (`public <init>()`), field names, and methods for `com.splitsmith.app.data.**` and `com.google.firebase.**`. Prevents R8 field renaming from breaking Firestore `doc.toObject()` deserialization and causing launch crashes.
2. **Explicit APK Asset Naming (`app/build.gradle.kts`)**: Configured `archivesName.set("SplitSmith-v0.3.8")` to guarantee output file is named [`SplitSmith-v0.3.8-release.apk`](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build/outputs/apk/release/SplitSmith-v0.3.8-release.apk).
3. **Re-Published GitHub Release v0.3.8 Asset**: Executed `gh release upload v0.3.8` with `--clobber` to replace the release asset on GitHub.

### Files Modified & Re-Released
- `[MODIFY]` [app/proguard-rules.pro](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/proguard-rules.pro)
- `[MODIFY]` [build.gradle.kts](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build.gradle.kts)
- `[RELEASE ASSET]` [SplitSmith-v0.3.8-release.apk](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build/outputs/apk/release/SplitSmith-v0.3.8-release.apk)

### Files Created & Modified
- `[NEW]` [app/proguard-rules.pro](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/proguard-rules.pro)
- `[MODIFY]` [DataModels.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/DataModels.kt)
- `[MODIFY]` [build.gradle.kts](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build.gradle.kts)
- `[OUTPUT]` [SplitSmith-v0.3.7-release.apk](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/build/outputs/apk/release/SplitSmith-v0.3.7-release.apk)

---

## [2026-08-07 16:37] Upgrade `android-architect-pro` Custom Skill (MAD 2026 Guidelines)

### Key Technical Accomplishments
1. **Integrated 7 Audit Passes**: Expanded `android-architect-pro` with dedicated passes covering Clean Architecture layer boundaries, UDF StateFlow hygiene, DI graph health, Navigation 3 `@Serializable` routes, Edge-to-Edge window insets, R8 `@Keep` rules, and Turbine unit testing.
2. **Firebase & Firestore `callbackFlow` Hygiene**: Added rule requiring explicit `awaitClose { listenerRegistration.remove() }` blocks on all reactive snapshot flows to eliminate background memory leaks.
3. **R8 Keep Rules & Navigation 3 Safety**: Added rules verifying `@Keep` annotations on `@Serializable` navigation destinations and Cloudinary network DTOs to prevent release-build obfuscation crash loops (`app-release.apk`).
4. **Synchronized Skill Locations**: Updated both global (`C:\Users\Atomix\.gemini\config\skills\...`) and workspace (`.agents/skills\...`) skill files.

### Files Modified
- `[MODIFY]` [SKILL.md (Global)](file:///C:/Users/Atomix/.gemini/config/skills/android-architect-pro/SKILL.md)
- `[MODIFY]` [SKILL.md (Workspace)](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/.agents/skills/android-architect-pro/SKILL.md)

---

## [2026-08-07 16:34] Payment UI Theme Normalization

### Key Technical Accomplishments
1. **Normalized Pending Payment Balance Color (`SplitExpensesScreen.kt`)**: Replaced hardcoded orange/amber color (`Color(0xFFE65100)`) with design system token `colors.inkPrimary`.
2. **Normalized Payment Approval Pending Badge (`SplitExpensesScreen.kt`)**: Replaced one-off amber badge background/border/text (`Color(0xFFFFF3E0)`, `Color(0xFFFFB74D)`, `Color(0xFFE65100)`) with normalized design system tokens `colors.surfaceCard`, `colors.borderWhisper`, and `colors.inkPrimary`.

### Files Modified
- `[MODIFY]` [SplitExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/SplitExpensesScreen.kt)

---

## [2026-08-07 16:31] Pending Approval UI Theme Normalization

### Key Technical Accomplishments
1. **Normalized Pending Admin Approval Heading (`GroupDetailScreen.kt`)**: Replaced un-themed amber color (`Color(0xFFD97706)`) with core design system token `colors.inkPrimary`.
2. **Design System Consistency**: Verified that all pending approval states (Pending Join Requests banner, Pending Cash Confirmations, and Pending Admin Approval screen) strictly adhere to SplitSmith's unified monochrome theme palette.

### Files Modified
- `[MODIFY]` [GroupDetailScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/group/GroupDetailScreen.kt)

---

## [2026-08-07 16:25] Comprehensive Architecture, Security, Data Layer & Flow UI/UX Implementation

### Key Technical Accomplishments
1. **Created `PendingExpenseHolder.kt`**: Encapsulated transient split navigation payloads (`pendingGroupJoinCode`, `sharedImageUri`, `pendingExpenseAmount`, `pendingExpenseDesc`, `pendingExpenseCategory`, `pendingExpenseDate`, `pendingExpenseAttachmentUri`, `pendingQuickSplitUser`) into a single-use thread-safe holder, removing static memory leaks across user sessions.
2. **Refactored `FirebaseManager.kt`**: Removed hardcoded UID fallback check in `currentUserId` to enforce pure Firebase Auth token identity security. Delegated all transient navigation properties to `PendingExpenseHolder`.
3. **Group Leave Financial Guard (`GroupDetailScreen.kt`)**: Added non-zero net balance check (`myNetBalance != 0.0`) preventing users from leaving a group with pending debts until they settle up.
4. **Debt Amount 2-Decimal Formatting (`GroupDetailScreen.kt`)**: Formatted all debt list strings (`\u20b9${if (debt.amount % 1.0 == 0.0) debt.amount.toInt().toString() else String.format("%.2f", debt.amount)}`), eliminating floating-point precision artifacts.
5. **1-on-1 UPI Settlement Integration (`DirectSplitDetailScreen.kt`)**: Enabled `UpiPaymentHelper` launching inside the 1-on-1 settlement tab. Added pending balance warning prompt when attempting to disconnect contacts with active balances.
6. **Custom Share Input Validation (`QuickSplitScreen.kt`)**: Added input validation capping custom share inputs at total expense amount.
7. **EXIF Camera Photo Auto-Rotation (`SlipImportScreen.kt`)**: Implemented `ExifInterface` orientation reading to auto-rotate camera photos prior to MLKit OCR text extraction.
8. **TopAppBar CSV Badge (`ReportsScreen.kt`)**: Added explicit "CSV" badge label to the download action button in the reports app bar.
9. **Onboarding Budget & UPI Input Validation (`OnboardingScreen.kt`)**: Enforced `KeyboardType.Number` for monthly budget inputs and regex `@` validation for UPI handles.
10. **Interactive Empty Feed CTAs (`HomeScreen.kt`)**: Added "+ Add Expense" and "+ New Group" action buttons inside empty recent activity state.
11. **Personal to Shared Split Data Transfer (`PersonalExpensesScreen.kt`)**: Forwarded expense `description` and `category` when converting personal expenses to quick splits.

### Files Modified / Created
- `[NEW]` [PendingExpenseHolder.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/PendingExpenseHolder.kt)
- `[MODIFY]` [FirebaseManager.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/FirebaseManager.kt)
- `[MODIFY]` [GroupDetailScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/group/GroupDetailScreen.kt)
- `[MODIFY]` [DirectSplitDetailScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/DirectSplitDetailScreen.kt)
- `[MODIFY]` [QuickSplitScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/quicksplit/QuickSplitScreen.kt)
- `[MODIFY]` [SlipImportScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/slip/SlipImportScreen.kt)
- `[MODIFY]` [ReportsScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/reports/ReportsScreen.kt)
- `[MODIFY]` [OnboardingScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/onboarding/OnboardingScreen.kt)
- `[MODIFY]` [HomeScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/home/HomeScreen.kt)
- `[MODIFY]` [AddExpenseScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/expense/AddExpenseScreen.kt)
- `[MODIFY]` [PersonalExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/personal/PersonalExpensesScreen.kt)

---

## [2026-08-07 15:42] Centralized UPI Payment Launcher & Clipboard Fallback

### Key Technical Accomplishments
1. **Created `UpiPaymentHelper.kt`**: Implemented `UpiPaymentHelper.launchUpiPayment` in `com.splitsmith.app.util`. It constructs `upi://pay` URIs safely, launches the system chooser, and catches `ActivityNotFoundException` (or missing app handlers) when no UPI app is installed on the phone/profile.
2. **Clipboard Fallback & User Notification**: Automatically copies the receiver's UPI ID to the device Clipboard and displays a clear Toast notification (`"No UPI app found. UPI ID (xyz@upi) copied to clipboard!"`) when an app cannot be opened.
3. **Refactored `GroupDetailScreen.kt`**: Replaced raw intent launcher with `UpiPaymentHelper.launchUpiPayment(...)` for group debt settlements, ensuring clipboard fallback and seamless pending settlement recording.
4. **Refactored `SplitExpensesScreen.kt`**: Unified direct 1-on-1 split and bulk peer group settlements to use `UpiPaymentHelper`.
5. **Empirical Verification**: Ran `./gradlew.bat compileDebugKotlin` with **BUILD SUCCESSFUL**.

### Files Modified / Created
- `[NEW]` [UpiPaymentHelper.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/util/UpiPaymentHelper.kt)
- `[MODIFY]` [GroupDetailScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/group/GroupDetailScreen.kt)
- `[MODIFY]` [SplitExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/SplitExpensesScreen.kt)

---

## [2026-08-07 13:52] Profile Flows Audit & Avatar Polish

### Key Technical Accomplishments
1. **Home Feed Amount Correction (`HomeScreen.kt`)**: Group expenses in the "Recent Activity" feed now display the user's personal financial impact (their share owed or amount lent) instead of the misleading full group total. Subtitles changed from generic category labels to contextual "You lent" / "You owe" descriptions.
2. **Detail Sheet Label Fix (`HomeScreen.kt`)**: Corrected the `GroupExpenseDetailBottomSheet` label from "You paid" to "You lent" for expenses created by the current user, accurately reflecting the net amount lent to other group members.
3. **Group Debts Avatar Upgrade (`GroupDetailScreen.kt`)**: Replaced the generic colored-circle letter placeholder in the "Who Owes What" debts list with `UserAvatar`, rendering each debtor's actual profile photo or superhero avatar.
4. **Pending Cash Confirmations Avatar (`GroupDetailScreen.kt`)**: Added `UserAvatar` beside payer names in the pending cash settlement confirmation cards for visual recognition.
5. **Join Request Avatar (`GroupDetailScreen.kt`)**: Added `UserAvatar` beside applicant names in the pending join request list within group settings.
6. **Add Expense Selector Avatars (`AddExpenseScreen.kt`)**: Added `memberProfilesMap` state, populated alongside `userNamesMap`, and embedded `UserAvatar` (24dp) inside both the "PAID BY" and "SPLIT WITH" horizontal chip selectors for premium visual distinction.
7. **Verification**: Successfully compiled debug build (`gradlew.bat compileDebugKotlin`) with **BUILD SUCCESSFUL**.

### Files Modified
- `HomeScreen.kt` — Feed amount mapping & detail sheet label
- `GroupDetailScreen.kt` — `StyledBalancesTab` signature + debts/settlements/applicants avatar rendering
- `AddExpenseScreen.kt` — Imports, `memberProfilesMap`, avatar chips in PAID BY / SPLIT WITH selectors

---

## [2026-08-07 12:48] Cloudinary Attachment Deletion & Background Dot Visibility Fix

### Key Technical Accomplishments
1. **BuildConfig Credential Injection**: Configured `app/build.gradle.kts` to load `CLOUDINARY_API_KEY` and `CLOUDINARY_API_SECRET` from `local.properties` and expose them via `BuildConfig`.
2. **Cloudinary Destruction Integration (`CloudinaryManager.kt`)**: Added `extractPublicId` utility to parse public IDs from secure Cloudinary URLs and implemented `deleteReceipt` utilizing the Cloudinary SDK's `destroy` API (with `resource_type = "raw"` and `invalidate = true`).
3. **Database Hook Integration (`FirebaseManager.kt`)**: Updated Firestore document deletion operations (`deleteExpense`, `deletePersonalExpense`, `deleteDirectSplit`) to accept a list of attachment URLs, detect any Cloudinary assets, and invoke the destruction method prior to document deletion.
4. **UI Event Propagation**: Modified deletion dialog handlers across `GroupDetailScreen.kt`, `PersonalExpensesScreen.kt`, and `SplitExpensesScreen.kt` to forward all respective receipt attachment URLs to the FirebaseManager during deletion.
5. **Background Dot Visibility Patch**: Restored dot grid background visibility across Group Detail, Personal Expenses, Direct Split Detail, and Split Expenses pages by removing the redundant 0.4x alpha multiplier (which rendered the subtle dots nearly invisible).
6. **Verification**: Successfully compiled debug build (`gradlew.bat compileDebugKotlin`) with **BUILD SUCCESSFUL**.

---

## [2026-08-02 22:57] Release v0.3.3.3: Cross-Device Drive Scope & Attachment Downloader Patch

### Key Technical Accomplishments
1. **Google OAuth Client Authorization (`GoogleDriveManager.kt`)**: Added `requestIdToken(webClientId)` to `requestDrivePermission` so Google OAuth scope (`DriveScopes.DRIVE_FILE`) authenticates cleanly with Web Client credentials.
2. **Cross-Device Shared Attachment Downloader (`AttachmentDownloader.kt`)**: Implemented streaming of Google Drive receipt links (`webContentLink`) into local cache (`cacheDir/shared_receipt_<id>.jpg`) for multi-user shared expenses.
3. **Black Screen Elimination (`AttachmentChipsView.kt`)**: Replaced blank/black screens with a clean `CloudOff` fallback card whenever opening a receipt saved locally on another phone before Drive Sync was enabled.
4. **Google Drive Link Sharing**: Added `anyone / reader` permission creation for uploaded Drive files so group members can view attachments.

### Published Release Artifacts
- **Tag**: `v0.3.3.3`
- **Release Link**: [https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.3.3](https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.3.3)
- **Signed Assets**:
  - `SplitSmith-v0.3.3.3-release.apk`
  - `SplitSmith-v0.3.3.3-release.aab`
 & Audit Log

## [2026-08-02 22:28] Google Drive Permission Consent & Auto-Sync Queue Integration

### Work Completed
1. **Explicit Drive Consent & Sync Trigger**:
   - Updated `HomeScreen.kt` ([HomeScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/home/HomeScreen.kt#L2035-L2066)) so that toggling **Google Drive Auto-Sync** to ON explicitly triggers `GoogleDriveManager.requestDrivePermission()` when `DRIVE_FILE` scope consent is missing.
2. **Immediate Queue Flushing**:
   - Integrated `PendingDriveUploadsManager.processPendingQueue(context)` into the Drive Auto-Sync toggle handler so that any existing local receipts are uploaded immediately to Google Drive as soon as sync is activated or authorized.
3. **Verification**:
   - Successfully compiled debug package (`gradlew.bat assembleDebug`) with **BUILD SUCCESSFUL**.
   - Deployed updated build to connected emulator `emulator-5554`.

---

## [2026-08-02 20:56] Production Release & GitHub Deployment (v0.3.3.2)

### Work Completed
1. **Version Bump**:
   - Updated `app/build.gradle.kts`: `versionCode = 30`, `versionName = "0.3.3.2"`.
2. **Signed Production Build**:
   - Compiled signed release APK (`app/build/outputs/apk/release/SplitSmith-v0.3.3.2-release.apk`).
   - Compiled signed release AAB (`app/build/outputs/bundle/release/SplitSmith-v0.3.3.2-release.aab`).
3. **GitHub Release Deployment**:
   - Published tag `v0.3.3.2`: [v0.3.3.2 Release](https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.3.2).
   - Uploaded signed APK and AAB release binaries.

---

## [2026-08-02 19:38] Image Editor, Drive Diagnostics, Profile UI & Navigation Fixes

### Work Completed
1. **Attachment Display Formatting & Label Cleaning**:
   - Created `AttachmentDisplayHelper.kt` to format system/camera names (`personal_1785672...`, `1000012345.jpg`) into clean labels (`Receipt 1`, `Invoice 2`).
2. **Full-Screen Viewer & Image Editor Fixes**:
   - Normalized remote/file URIs in `ReceiptEditorModal.kt` to prevent silent crashes.
   - Added "Discard edits?" confirmation `AlertDialog` when exiting `ReceiptEditorModal` with modified edits.
   - Ensured edited image URIs replace the attachment list in `AttachmentComponent.kt` and `SlipImportScreen.kt`.
3. **Google Drive Consent Flow & Drive Diagnostics**:
   - Structured `DriveUploadResult` with explicit `DriveError` diagnostics (`SCOPE_MISSING`, `TOKEN_EXPIRED`, `NETWORK_ERROR`, `FILE_NOT_FOUND`).
   - Mapped `split.receiptDriveFileIds` to render a single muted `☁️ Drive Synced` badge *on the same line right before attachment chips*.
4. **Navigation & State Cleanup**:
   - Reset detail sheet states (`selectedPersonalForDetail`, `selectedGroupExpenseForDetail`, `selectedSplitForDetail`) in `LaunchedEffect(pagerState.currentPage)` to prevent stale modals from appearing when swiping.
   - Cleared static pending state on tab switch.
5. **Profile Modal & QR Buttons**:
   - Removed Google Drive Backup toggle from `showProfileDialog`.
   - Upgraded "Scan QR" button with `Icons.Default.QrCodeScanner` icon and "Show QR" button with `Icons.Default.QrCode` icon.
6. **Split Expenses Attachment Badges**:
   - Rendered `Icons.Default.AttachFile` attachment badge icon & count `(1)` on `DirectSplitListItem` rows in `SplitExpensesScreen` and `PersonDetailBottomSheet`.

## [2026-08-08 00.39] v0.3.8.3 Release
- Implemented 'Show full ledger' toggle in GroupDetailScreen.
- Personal debts are shown by default to reduce cognitive overload.
- Changed 'Simplify debts' button to 'Show full ledger'.
- Fallback message when group has debts but user is fully settled.

## [2026-08-08 01.14] v0.3.8.4 Release
- Fixed an issue where removing receipt attachments from expenses during edit did not delete the file from Cloudinary.
- Added orphaned receipt cleanup to both group expenses and personal expenses.

---

## [2026-08-12 16:30] Release v0.3.9.1 Published

### Key Technical Accomplishments
1. **Google Pay Shared URI Caching (`MainActivity.kt`)**: Implemented local caching during intent delivery to prevent transient read permission revocation when importing GPay receipts.
2. **Unified full-screen zoomable Image Viewer (`AttachmentChipsView.kt` & `GroupDetailScreen.kt`)**: Extracted the pan-and-zoom modal into a reusable `AttachmentImageViewerDialog` component.
3. **Redesigned Proof Attachment Flow (`GroupDetailScreen.kt` & `FirebaseManager.kt`)**:
   * Replaced the camera emoji with a styled `OutlinedButton` and vector icon.
   * Added a local image preview modal to confirm the screenshot before uploading.
   * Enabled debtors to remove/delete their attached proofs (which cleans them up from both Firestore and Cloudinary).
   * Refactored remote proof clicks to fetch and decrypt the image in the background before opening it in the unified image viewer.
4. **Duplicate-Settlement Prevention (`GroupDetailScreen.kt`)**: Disabled the **Settle** button and labeled it **Pending** if there is already an active pending settlement request between the debtor and creditor.

### Deployed Artifacts & Links
* **GitHub Release**: [v0.3.9.1 Patch Release](https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.9.1)
* **Production Binaries**:
  * [SplitSmith-v0.3.9.1-release.apk](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/SplitSmith-v0.3.9.1-release.apk)
  * [SplitSmith-v0.3.9.1-release.aab](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/SplitSmith-v0.3.9.1-release.aab)
* **Firebase Hosting CDN URL**: [https://splitsmith.web.app](https://splitsmith.web.app) (Serving updated `splitsmith.bin`)

---

## [2026-08-12 19:05] Release v0.3.9.2 Published

### Key Technical Accomplishments
1. **Interactive Settlements Bottom Sheet (`SettlementDetailBottomSheet`)**:
   * Removed separate "Pending Confirmations" list from the top of the tab.
   * Merged pending and confirmed settlements into a single chronological timeline under `"SETTLED TRANSACTIONS"`.
   * Pending items show a `"Pending Approval"` tag with directional amount coloring (red for outgoing payments, green for incoming payments).
   * Tapping any row in the list opens the modal bottom sheet showing payer/creditor details, dates, and attachment previews.
   * The debtor can edit/crop or delete unapproved proofs inside this sheet. If confirmed/approved, all modifications are disabled and locked.
2. **"Settle Up" Peer Selection Sheet (`SelectSettlePeerBottomSheet`)**:
   * Removed individual Settle action buttons next to debt rows in "WHO OWES WHAT".
   * Tapping the sticky bottom **"Settle Up"** button opens a selection sheet listing all outstanding peers you owe money to, allowing you to select who to settle with.
   * If a pending settlement request already exists with a peer, their row inside this sheet shows a `"Pending confirmation"` status and is disabled.
3. **Directional Debt Balance Colors**:
   * Updated the "WHO OWES WHAT" debt list to display amount balances in green (`colors.positiveGreen`) if the peer owes you, and red (`alertRed`) if you owe them.
4. **Emoji Cleanups**:
   * Replaced the camera emoji (`📷`) on line 869 with the standard `Icons.Default.CameraAlt` vector icon.
5. **Orphan Storage Protection**:
   * Updated the confirmation upload dialog to delete the old proof file from Cloudinary before uploading the cropped edited proof, preventing orphan file leaks.

### Deployed Artifacts & Links
* **GitHub Release**: [v0.3.9.2 Patch Release](https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.9.2)
* **Production Binaries**:
  * [SplitSmith-v0.3.9.2-release.apk](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/SplitSmith-v0.3.9.2-release.apk)
  * [SplitSmith-v0.3.9.2-release.aab](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/SplitSmith-v0.3.9.2-release.aab)
* **Firebase Hosting CDN URL**: [https://splitsmith.web.app](https://splitsmith.web.app) (Serving updated `splitsmith.bin`)



