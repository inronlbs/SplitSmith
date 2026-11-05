## [2026-11-05 16:50] Home Screen Architecture & UDF Hardening (MAD 2026 Standards)

### Features & Fixes
1. **Separation of Concerns & ViewModel Extraction (`HomeViewModel.kt`)**:
   - Extracted multi-source Firestore flow aggregation (personal expenses, direct splits, group shares, user profile) out of the UI composition into `HomeViewModel`.
   - Unified all dashboard state fields into a single immutable `HomeDashboardUiState` data class exposed via `StateFlow` with `SharingStarted.WhileSubscribed(5000)`.
   - Pure domain calculation method `calculateDashboardState` handles financial aggregation, calendar-based pacing, 7-day spending trends, and category distribution independently from Android UI lifecycles.
2. **Lifecycle-Aware Collection (`HomeScreen.kt`)**:
   - Replaced unmanaged Compose `collectAsState()` with `collectAsStateWithLifecycle()`, guaranteeing upstream Firestore snapshot listeners automatically pause 5 seconds after the app is backgrounded.
   - Refactored `HomeDashboardView` into a purely declarative UI component consuming `state: HomeDashboardUiState`.
3. **Safe Coroutine Scopes for Data Mutations**:
   - Transferred settlement confirmation/decline actions and avatar uploads into `viewModelScope` so network mutations survive device rotation and screen transitions without risk of cancellation.
4. **Comprehensive Automated Unit Testing (`HomeViewModelTest.kt`)**:
   - Implemented 5 unit tests verifying zero-input defaults, net position math, calendar budget and daily allowance pacing, pending settlement detection, and chronological activity sorting.

### Files Modified
- `[NEW]` [HomeViewModel.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/home/HomeViewModel.kt)
- `[MODIFY]` [HomeScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/home/HomeScreen.kt)
- `[NEW]` [HomeViewModelTest.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/test/java/com/splitsmith/app/ui/home/HomeViewModelTest.kt)

### Verification & Quality Gates
- **Gradle Kotlin Compilation**: `./gradlew compileDebugKotlin` passed with code 0 (`BUILD SUCCESSFUL in 39s`).
- **Unit Test Execution**: `./gradlew testDebugUnitTest` executed 27 tasks and passed with code 0 (`BUILD SUCCESSFUL in 23s`).
- **Zero Emoji Audit**: Confirmed 0 emojis across all code, tests, and commit messages.

---

## [2026-11-05 16:35] Home Screen Overhaul: Financial Command Center

### Features & Fixes
1. **Brand Header & Dynamic Greeting (`HomeScreen.kt`)**:
   - Clean top app bar with `SplitSmith` brand typography, pending settlement notification bell indicator, QR scanner launcher, and profile modal trigger.
   - Dynamic time-based greeting (*"Good morning / afternoon / evening, [FirstName]"*) placed directly above the hero card with the subtitle *"Here is your financial overview"*.
2. **Unified Net Position Hero Card**:
   - Prominent net position figure (`+₹...` / `-₹...`) in JetBrains Mono 28sp with clear status badge (*"Owed to you"* / *"You owe"* / *"Settled"*).
   - Real-time sub-row breaking down total unsettled `Receivable: ₹...` and `Payable: ₹...`.
   - Monthly budget bar with smooth animated progress and daily allowance pacing (*"Daily allowance: ₹.../day for next X days"*).
3. **Option A Quick Actions Strip**:
   - 3 prominent thumb-friendly buttons:
     - `[Scan Bill]` (solid `inkPrimary`): Triggers receipt scanner options bottom sheet.
     - `[Split Bill]` (outlined `borderWhisper`): Launches quick 1-on-1 split workflow.
     - `[Log Expense]` (outlined `borderWhisper`): Jumps directly to Personal Expense tracker with add sheet opened.
4. **Action Required Banner (Conditional)**:
   - Appears dynamically when direct settlements require current user confirmation with instant `Confirm` and `Decline` actions wired to `FirebaseManager`.
5. **Spending Insights (Options B + C Blended)**:
   - Inline 2-pill toggle:
     - **7-Day Trend**: Minimal linear vertical bars for Mon-Sun showing daily spend, current day highlighted with active dot, subtle dashed budget benchmark line without cluttering text or average badges.
     - **Categories**: Multi-category segmented proportion bar + Top 3 categories with exact amounts and percentages.
6. **Curated Latest Transactions (3 Items Only)**:
   - Replaced crowded 12-item raw list with 3 curated rows featuring clear origin badges (`[SPLIT]`, `[SOLO]`, `[GROUP]`).
   - Dual footer links: `[All Splits →]` and `[All Expenses →]` for seamless navigation to the respective detailed tabs.
7. **Monochrome Design Coherence & Zero Emojis**:
   - Pure typography and monochrome design tokens (`canvasChalk`, `inkPrimary`, `surfaceCard`, `borderWhisper`, `alertRed`, `OutfitFamily`, `JetBrainsMonoFamily`).
   - Strict 0-emoji compliance.

### Files Modified
- `[MODIFY]` [HomeScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/home/HomeScreen.kt)

### Verification & Quality Gates
- **Gradle Kotlin Compilation**: `./gradlew compileDebugKotlin` passed with code 0 (`BUILD SUCCESSFUL in 49s`).
- **Unit Test Execution**: `./gradlew testDebugUnitTest` executed 27 tasks and passed with code 0 (`BUILD SUCCESSFUL in 40s`).
- **Zero Emoji Audit**: Passed strict automated audit with 0 emojis across git diff.

---

## [2026-11-05 15:18] WhatsApp-Style Search with On-Device Contact & Group Member Cache

### Features & Fixes
1. **On-Device Persistent Contact Cache (`LocalContactCache.kt`, `FirebaseManager.kt`, `MainActivity.kt`)**:
   - Implemented `LocalContactCache` using Android `SharedPreferences` + `JSONObject` to persist known user profiles locally on device.
   - Initialized at startup in `MainActivity.onCreate()` to populate memory instantly.
   - Integrated with `FirebaseManager.getUserProfile()` and `getCachedUserProfile()` so searches run 100% on-device without triggering unnecessary Firestore network calls.
2. **Mutual Group Members & Connected Users in Search (`SplitExpensesScreen.kt`)**:
   - `LaunchedEffect(directSplits, groups, connectedUsers)` pre-caches all known member UIDs across all active user groups and connected contacts in the background.
   - **Search Active (`searchQuery.isNotBlank()`)**: Queries across split peers, connected friends, and all mutual group members instantly on-device.
   - **Main Feed Cleanliness (`searchQuery.isBlank()`)**: Follows the WhatsApp pattern where contacts and group members with 0 splits remain hidden from the main feed, keeping it uncluttered.
3. **Contextual Search Item Rendering (`IndividualPeerListItem`)**:
   - Differentiates search results:
     - Peers with splits: Displays split count and balance badge (`+₹...`, `-₹...`, `"settled"`).
     - Connected friends with 0 splits: Subtitle `"Connected friend"` with a navigation chevron (`KeyboardArrowRight`).
     - Group members with 0 splits: Subtitle `"Group member"` with a navigation chevron (`KeyboardArrowRight`).
   - Tapping opens `DirectSplitDetailScreen` to immediately start a split.

### Files Modified
- `[NEW]` [LocalContactCache.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/LocalContactCache.kt)
- `[MODIFY]` [FirebaseManager.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/FirebaseManager.kt)
- `[MODIFY]` [MainActivity.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/MainActivity.kt)
- `[MODIFY]` [SplitExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/SplitExpensesScreen.kt)

### Verification & Quality Gates
- **Gradle Kotlin Compilation**: `./gradlew compileDebugKotlin` passed with code 0 (`BUILD SUCCESSFUL in 48s`).
- **Unit Test Execution**: `./gradlew testDebugUnitTest` executed 27 tasks and passed with code 0 (`BUILD SUCCESSFUL in 24s`).
- **Zero Emoji Audit**: Confirmed 0 emojis in git diff and all UI strings.

---

## [2026-11-05 13:45] Two-Pillar Bill Import Pipeline (Split vs Expenses)

### Features & Fixes
1. **Two-Pillar Destination Pipeline (`SlipImportScreen.kt`)**:
   - Replaced the previous 3 disparate buttons with 2 clear, organized destination pillars:
     - **"Split Bill"** (filled primary button, `inkPrimary`): Opens a dedicated modal sheet showing all Split Groups (`!group.isTracker`) and 1-on-1 Connected Friends, with one-tap entry staging into `AddExpenseScreen` or `QuickSplitScreen`. Also includes a direct "Split with new / other contact" option.
     - **"Log Expense"** (outlined button, `borderWhisper`): Opens a dedicated modal sheet displaying the preset "Personal Expenses" tracker (1 participant - You) at the top, followed by all collaborative "Shared Expense Groups" (`group.isTracker`).
2. **Zero Duplicate Logging & Instant Staging**:
   - Integrated duplicate detection checks before logging personal expenses directly from the sheet.
   - Preserves scanned receipt attachments (including cloud vs local storage selection) when routing to personal expenses or staging for group / quick split.
3. **Monochrome Design Coherence & Zero Emojis**:
   - Uses `GroupIconView` and `UserAvatar` with standard monochrome design tokens (`canvasChalk`, `inkPrimary`, `surfaceCard`, `borderWhisper`, `OutfitFamily`, `textLabelMedium`).
   - Zero emojis across code, labels, and toasts.

### Files Modified
- `[MODIFY]` [SlipImportScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/slip/SlipImportScreen.kt)

### Verification & Quality Gates
- **Gradle Kotlin Compilation**: `./gradlew compileDebugKotlin` exited with code 0 (`BUILD SUCCESSFUL in 33s`).
- **Unit Test Execution**: `./gradlew testDebugUnitTest` executed 27 tasks and passed with code 0 (`BUILD SUCCESSFUL in 21s`).
- **Zero Emoji Audit**: Confirmed 0 emojis in git diff.

---

## [2026-11-04 17:45] Group Mode Immutability & Clean Expense Tab Separation

### Features & Fixes
1. **Group Mode Immutability (`GroupDetailScreen.kt`)**:
   - Removed the mutable `Switch` toggle from `StyledSettingsTab` in `GroupDetailScreen`. Group mode is strictly permanent once created, safeguarding historical expenses, splits, and debt balances from accidental corruption.
   - Replaced with a read-only group mode indicator badge: `"Split Group (With Balances)"` or `"Expense Tracker (No Debts)"` marked with a `"Permanent"` badge.

2. **Clean Domain & Tab Separation (`SplitExpensesScreen.kt`, `PersonalExpensesScreen.kt`)**:
   - Filtered `SplitExpensesScreen` to display **only Split Groups** (`!group.isTracker`) alongside 1-on-1 direct splits.
   - Collaborative Expense Tracker groups now live exclusively in their dedicated home: the `"Expense Groups"` segment of `PersonalExpensesScreen`.
   - Removed redundant filter pills (`[All]`, `[Split Groups]`, `[Trackers]`) and group badges from `SplitExpensesScreen`.

3. **Pure Monochrome Design System Alignment (`SplitExpensesScreen.kt`)**:
   - Eliminated the non-monochrome color leak on the participant counter subtitle in `CreateGroupBottomSheet` by switching from `positiveGreen` to `inkPrimary`.
   - All interactive controls, badges, and surfaces in `SplitExpensesScreen` and `GroupDetailScreen` now strictly adhere to the chalk-and-ink monochrome palette (`canvasChalk`, `inkPrimary`, `surfaceCard`, `borderWhisper`).

### Files Modified
- `[MODIFY]` [GroupDetailScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/group/GroupDetailScreen.kt)
- `[MODIFY]` [SplitExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/SplitExpensesScreen.kt)

### Verification & Quality Gates
- **Gradle Kotlin Compilation**: `./gradlew compileDebugKotlin` exited with code 0 (`BUILD SUCCESSFUL`).
- **Unit Test Execution**: `./gradlew testDebugUnitTest` executed 27 tasks and passed with code 0 (`BUILD SUCCESSFUL in 25s`).

---

## [2026-11-04 16:30] WhatsApp-Style Group Creation, Live Contact Search & Expense Tracker Mode Fix

### Features & Fixes
1. **Expense Tracker Mode Toggle & Dual-Key Firestore Fix (`DataModels.kt`, `FirebaseManager.kt`)**:
   - Fixed Firestore JavaBean deserialization mismatch where `isExpenseTracker` was stored or read under `expenseTracker` due to Java getter naming conventions.
   - Dual-mapped `@get:PropertyName("isExpenseTracker")` and `@get:PropertyName("expenseTracker")` in `Group` model, with a computed helper `val isTracker: Boolean get() = isExpenseTracker || expenseTracker`.
   - Updated `updateGroupTrackerMode` and `createGroup` in `FirebaseManager` to write both keys atomically so backward compatibility is 100% maintained.
   - Added optimistic UI state in `GroupDetailScreen` settings tab so the toggle response feels instantaneous without snapping back.
   - Fixed crash/validation bug in `FirebaseManager.addExpense` allowing empty splits when `splitMode == "NONE"` (tracker mode).

2. **WhatsApp-Style 2-Step Group Creation Flow (`SplitExpensesScreen.kt`)**:
   - Overhauled `CreateGroupBottomSheet` from a cluttered single-page form into WhatsApp's progressive disclosure pattern:
     - **Step 1 ("Add Participants")**: Full contact list surfaced instantly, live name-prefix search bar, horizontal staged member chips strip with remove buttons, and checkboxes. Users can tap "Next" or "Skip" to create without members.
     - **Step 2 ("New Group Details")**: Group title, category icon selector, and clear visual selection cards for **Split Group** (debts, balances, settlements) vs **Expense Tracker** (budget tracking, zero debts).

3. **Unified Contact Engine & Frictionless Member Management (`FirebaseManager.kt`, `GroupDetailScreen.kt`)**:
   - Implemented `getUnifiedConnectedContacts()` in `FirebaseManager` combining direct connections (`users/{uid}/connections`), 1-on-1 split peers (`direct_splits`), and all mutual group members across user's active groups.
   - Added `batchInviteMembers(groupId, targetUserIds)` for atomic multi-member invitations in a single batch write.
   - Revamped `StyledSettingsTab` member invitation in `GroupDetailScreen`:
     - Added 1-tap "Copy Invite Link" / "Share QR Code" directly inside the invitation card.
     - "Quick Add Friends" horizontal carousel showing suggested connected contacts not yet in the group.
     - Instant name-prefix search filtering connected contacts with staged multi-select chips and a batch "Add Members" button.

4. **Split Expenses Screen Group Filtering & Badges (`SplitExpensesScreen.kt`)**:
   - Added `groupTypeFilter` with filter pills: `[All]`, `[Split Groups]`, and `[Trackers]`.
   - Added clear visual badges (`[Tracker]` vs `[Split]`) on group cards in the list.

### Files Modified
- `[MODIFY]` [DataModels.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/DataModels.kt)
- `[MODIFY]` [FirebaseManager.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/data/FirebaseManager.kt)
- `[MODIFY]` [SplitExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/split/SplitExpensesScreen.kt)
- `[MODIFY]` [GroupDetailScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/group/GroupDetailScreen.kt)
- `[MODIFY]` [PersonalExpensesScreen.kt](file:///C:/Users/Atomix/Documents/antigravity/lively-babbage/splitsmith/app/src/main/java/com/splitsmith/app/ui/personal/PersonalExpensesScreen.kt)

### Verification & Quality Gates
- **Gradle Kotlin Compilation**: `./gradlew compileDebugKotlin` exited with code 0.
- **Unit Test Execution**: `./gradlew testDebugUnitTest` executed 27 tasks and passed with code 0 (`BUILD SUCCESSFUL`).

---

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

---

## [2026-08-12 22:42] User Search & Scan-to-Connect Core Diagnostics & Fixes

### Key Technical Accomplishments
1. **Firestore Rules Update (`firestore.rules`)**:
   * Allowed authenticated users to write connection records under another user's connections subcollection, provided the document ID (`connectionId`) matches their own authenticated UID (`request.auth.uid == connectionId`). This fixes the mutual connection write failure in `addConnection()`.
2. **Scanner Self-Healing (`HomeScreen.kt`)**:
   * Refactored QR scanner callback to attempt to resolve scanned values globally as user profiles first (supporting raw codes, emails, and full URIs). Only falls back to group join codes if user resolution fails.
3. **Connections Database Merging (`FirebaseManager.kt`)**:
   * Added `getConnectedUserProfiles()` helper function to query and resolve connection profiles from subcollections.
   * Merged connections and direct contacts in `searchUsersInstantly()` to guarantee scanned friends display in local search queries instantly.
4. **Always-On Remote Search Trigger (`QuickSplitScreen.kt`)**:
   * Removed the blocker where local friend matches hid the global search card, allowing users to query SplitSmith globally anytime their query is >= 2 characters.

---

## [2026-08-12 23:28] Database Audit, Cleanup, & Security Rules Hardening

### Key Technical Accomplishments
1. **Firestore Database Purge (`cleanup_database.js`)**:
   * **Duplicate Profiles Deleted**: Removed legacy user document IDs `0r4cYxLrGIZm4p0LQ3ZkVvG3MSh1` and `Hy4ucoEtjBUo10v6AmS8zvsc1Ru1` from the `/users` collection.
   * **Orphan Connections Cleared**: Cleared connections pointing to legacy UIDs from active user documents (e.g. Abhijith and Amal Tom's active profiles).
   * **Ghost User "Friend" Purged**: Deleted the corrupted direct split document `5lVNWLlxo2LH4uOr8Jaz` containing an empty `withUser` field which rendered as "Friend" in the app.
   * **Settle Test Transactions Cleared**: Deleted 4 other settled direct splits and 2 settled group settlements created for testing to clean up UI clutter.
2. **Security Rules Hardening (`firestore.rules`)**:
   * Removed the legacy email and UID bypasses from the `isUser(uid)` helper function in both the root and subproject `firestore.rules` files:
     ```javascript
     function isUser(userId) {
       return isAuthenticated() && request.auth.uid == userId;
     }
     ```
     *(This locks down the database so that only the authenticated owner can access or write to their own profile, with no exceptions).*
   * Deployed the rules successfully using the Firebase CLI to release `firestore.rules`.

---

## [2026-08-13 01:07] Friends Integration, Search Bar Fix & Minimal Filters

### Key Technical Accomplishments
1. **Unified People Tab & Connections Integration**:
   * Merged the active connection stream (`observeConnections()`) with the splits history stream (`observeDirectSplits()`) in `SplitExpensesScreen.kt`.
   * Displays all connected users in the **People** tab, even if their net balance is `₹0` (which renders as `"Settled up"`).
   * Put active balances at the top of the list, followed by settled friends in alphabetical order.
2. **Search Bar Stability & Focus Fix**:
   * Wrapped unstable horizontal pager lambdas in `HomeScreen.kt` with `remember` to prevent recreating children views on parent recompositions.
   * Utilized `rememberSaveable { mutableStateOf("") }` for `searchQuery` in `SplitExpensesScreen.kt` to ensure focus and keyboard input are maintained seamlessly.
   * Enhanced the local search query matching algorithm to check the contact's email address and 6-letter User Code in addition to their display name and transaction descriptions.
3. **Targeted, Toggleable Search & Minimal Filters**:
   * Added a clickable search icon to the right of the tab rows in both `GroupDetailScreen.kt` (Expenses/Balances tabs) and `PersonDetailBottomSheet` (Expenses/Balances/Settings tabs). Tapping it slides open the search field.
   * **Filter Icon Toggler**: Placed a settings/filter icon (`Icons.Default.Tune`) inside the trailing end of the search bar. Tapping it toggles the expansion of the filter row dynamically. By default, the filter pills are hidden.
   * Implemented a compact **2-pill filter layout** (no sorting menu to avoid layout clutter) under the animated toggle:
     * **Payer Pill**: Cycles between `Payer: All` ➔ `Payer: Me` ➔ `Payer: Others` on click.
     * **Category Pill**: Toggles a compact dropdown menu dynamically populated with categories from the active expenses list.
4. **2-Step Disconnect & Delete Flow**:
   * Implemented settings buttons in `PersonDetailBottomSheet`:
     * **Disconnect Friend**: Deletes mutual connection documents in Firestore (removing them from active quick-split networks).
     * **Delete Friend & History**: Active only when disconnected and net balance is `₹0`. Purges the entire direct splits history with that peer via `deleteDirectSplitsWithUser` in `FirebaseManager.kt`, removing them from the app completely.
5. **Gradle Build Verification**:
   * Successfully verified that all changes compile, run, and pass Android compilation checks: `./gradlew assembleDebug` exited with code 0.

---

## [2026-08-13 01:19] Release v0.3.9.3 Published

### Version
- **versionCode**: `53` → `54`
- **versionName**: `0.3.9.2` → `0.3.9.3`
- **Type**: Patch Release

### Build
- `./gradlew assembleRelease bundleRelease` — **BUILD SUCCESSFUL** in 2m 45s
- Signed APK: `SplitSmith-v0.3.9.3-release.apk`
- Signed AAB: `SplitSmith-v0.3.9.3-release.aab`

### GitHub Release
- ✅ Published: https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.9.3
- Both APK and AAB uploaded as release assets

### Firebase Hosting CDN
- ✅ `public/splitsmith.bin` updated with v0.3.9.3 APK
- ✅ Deployed: https://splitsmith.web.app

### Git
- ✅ Committed: `fix: v0.3.9.3 patch - toggleable search filters, connected friends tab, search focus fix, disconnect/delete flow`
- ✅ Pushed to `main` — commit `5c01bf3`
- 9 files changed, 542 insertions(+), 80 deletions(-)

---

## [2026-08-13 01:29] Release v0.3.9.4 Published

### Version
- **versionCode**: `54` → `55`
- **versionName**: `0.3.9.3` → `0.3.9.4`
- **Type**: Patch Release

### Changes
- Fixed search bar text and placeholder getting clipped in Group Detail and Friend Splits views
- Root cause: dynamic `Row` with conditional Clear button inside `trailingIcon` caused variable measured width, shrinking the text field's available space
- Fix: replaced with a single static `IconButton(Tune)` — stable trailing width, no more clipping

### Build
- `./gradlew assembleRelease bundleRelease` — **BUILD SUCCESSFUL** in 2m 18s
- Signed APK: `SplitSmith-v0.3.9.4-release.apk`
- Signed AAB: `SplitSmith-v0.3.9.4-release.aab`

### GitHub Release
- ✅ Published: https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.9.4
- Both APK and AAB uploaded as release assets

### Firebase Hosting CDN
- ✅ `public/splitsmith.bin` updated with v0.3.9.4 APK
- ✅ Deployed: https://splitsmith.web.app

### Git
- ✅ Committed: `fix: v0.3.9.4 patch - fix search bar text clipping in group and friend split views`
- ✅ Pushed to `main` — commit `9872a40`
- 6 files changed, 43 insertions(+), 30 deletions(-)

## [2026-08-13 01:46] Release v0.3.9.5 Published

### Summary
Patch release fixing search bar text being clipped (cut in half) in the Group Expenses tab.

### Root Cause
OutlinedTextField at GroupDetailScreen.kt:L1154 had a hard Modifier.height(44.dp) constraint. Material 3's OutlinedTextField enforces a minimum internal height of ~56dp. Forcing it to 44dp caused Compose to clip the draw surface — text rendered at its natural Y-center but the bottom ~12dp of glyphs were outside the clipping boundary, appearing cut in half. A secondary fix attempt adding contentPadding failed compilation because that parameter only exists on the newer TextFieldState overload, not the alue/onValueChange overload. The final fix: simply remove the hard height() constraint and let the field size naturally.

### Changes Made
- **[MODIFY]** `GroupDetailScreen.kt`: Removed `Modifier.height(44.dp)` from `OutlinedTextField` in `StyledExpensesTab` (L1154).
- **[MODIFY]** `app/build.gradle.kts`: Bumped `versionCode` 55 → 56, `versionName` `0.3.9.4` → `0.3.9.5`.
- **[MODIFY]** `firebase.json`: Updated `Content-Disposition` download filename to `SplitSmith-v0.3.9.5-release.apk`.

### Build Verification
- **Compile Status**: `BUILD SUCCESSFUL` in 2m 23s
- **Signed APK**: `SplitSmith-v0.3.9.5-release.apk`
- **Signed AAB**: `SplitSmith-v0.3.9.5-release.aab`

### Release Artifacts
- **GitHub Release**: https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.9.5
- **Firebase Hosting CDN**: https://splitsmith.web.app (splitsmith.bin updated)

### Git
- ✅ Committed: `fix: v0.3.9.5 patch - search bar text clipping fix`
- ✅ Pushed to `main` — commit `9c35fc5`
- 5 files changed, 35 insertions(+), 3 deletions(-)

## [2026-08-13 02:07] Release v0.3.9.6 Published

### Summary
Patch release resolving missing Edit & Delete options in expense detail bottom sheet cards across Group Expenses, Dashboard expense previews, and Direct 1-on-1 Splits.

### Root Cause
1. GroupDetailScreen.kt checked al isCreator = expense.createdBy == FirebaseManager.currentUserId. For all legacy expenses created before createdBy was tracked in Firestore, createdBy is "". "" == currentUserId returned alse, hiding the 3-dot management menu for all legacy group expenses.
2. SplitExpensesScreen.kt (Direct 1-on-1 Splits) gated the options menu behind if (!isSettled) and only provided "Delete Split", omitting "Edit Split".
3. HomeScreen.kt (GroupExpenseDetailBottomSheet) lacked a 3-dot management menu entirely on dashboard preview cards.

### Changes Made
- **[MODIFY]** `GroupDetailScreen.kt`: Updated permission check in `GroupExpenseDetailBottomSheet` to allow expense management if current user is creator, payer, or if `createdBy` is blank (legacy fallback).
- **[MODIFY]** `SplitExpensesScreen.kt`: Removed `if (!isSettled)` menu guard in `DirectSplitDetailBottomSheet` and added leading icons for menu items.
- **[MODIFY]** `HomeScreen.kt`: Added 3-dot options menu with Edit and Delete capabilities to `GroupExpenseDetailBottomSheet` on Dashboard cards.
- **[MODIFY]** `app/build.gradle.kts`: Bumped `versionCode` 56 → 57, `versionName` `0.3.9.5` → `0.3.9.6`.
- **[MODIFY]** `firebase.json`: Updated `Content-Disposition` download filename to `SplitSmith-v0.3.9.6-release.apk`.

### Build Verification
- **Compile Status**: `BUILD SUCCESSFUL` in 2m 32s
- **Signed APK**: `SplitSmith-v0.3.9.6-release.apk`
- **Signed AAB**: `SplitSmith-v0.3.9.6-release.aab`

### Release Artifacts
- **GitHub Release**: https://github.com/inronlbs/SplitSmith/releases/tag/v0.3.9.6
- **Firebase Hosting CDN**: https://splitsmith.web.app (splitsmith.bin updated)

### Git
- ✅ Committed: `fix: v0.3.9.6 patch - resolve missing edit/delete options in expense detail cards`
- ✅ Pushed to `main` — commit `4df32ab`
- 7 files changed, 130 insertions(+), 31 deletions(-)


## [2026-11-04 22:25] Full-Screen Member Picker, Member Removal RBAC & Group Mode Cleanup

### Changes Implemented
1. **Firebase Data Layer (`FirebaseManager.kt`)**:
   - Implemented `removeMemberFromGroup(groupId, targetUserId)` using atomic Firestore transaction.
   - Enforced server-side RBAC clearance checks: callers must be owner or admin; group owner cannot be removed; co-admins can only be removed by the owner.
   - Implemented `batchInviteMembers(groupId, targetUserIds)` to stage multiple participant additions atomically.

2. **Group Settings & Member Picker (`GroupDetailScreen.kt`)**:
   - Removed the obsolete "GROUP TYPE / MODE" card completely.
   - Added `(+)` circular quick-add button in the "QUICK ADD FRIENDS" section.
   - Added full-screen `AddMembersFullScreenSheet` expanding from the bottom (`skipPartiallyExpanded = true`, `fillMaxSize()`), styled with the monochrome system (zero emojis):
     - Top navigation bar with Back button, participant count, and Done button.
     - Search bar for live filtering of connected contacts by name or email.
     - WhatsApp-like staged selected contact chips row with quick unselect `(x)`.
     - Contact list with custom monochrome checkboxes.
     - Full-width bottom "Add Members" action bar.
   - Added member removal clearance logic in the members list:
     - Owners can remove co-admins and members.
     - Co-admins can remove regular members only.
     - Red "Remove" action button with confirmation dialog.

3. **Design System & Zero Emojis**:
   - Verified 100% monochrome design conformance (`canvasChalk`, `inkPrimary`, `surfaceCard`, `borderWhisper`).
   - Zero emojis across all modified code, UI strings, dialogs, and toasts.

### Verification & Testing
- `compileDebugKotlin`: SUCCESSFUL (53s)
- `testDebugUnitTest`: SUCCESSFUL (41s)


## [2026-11-05 10:43] Minimal Reimagined Split & Expenses Architecture

### Architectural Changes Implemented
1. **Unified Social Debt Hub (`SplitExpensesScreen.kt`)**:
   - Merged Split Groups and 1-on-1 Friends into a single, cohesive WhatsApp-style feed.
   - Introduced sealed interface `SplitFeedItem` supporting both `GroupEntry` and `PeerEntry`.
   - Added minimal, compact filter pills: `All`, `Groups`, and `Friends`.
   - Standardized minimal row items (`IndividualPeerListItem` and `GroupListItem`):
     - Displays avatar (group icon or user avatar).
     - Clean semi-bold typography for titles and split count captions.
     - JetBrains Mono mono amounts for net balances: `+₹X` in primary ink, `-₹Y` in alert red, or `settled` in muted ink.
     - Ultra-thin monochrome dividers (`borderWhisper`, 0.5.dp).

2. **Unified Expense Tracking Hub (`PersonalExpensesScreen.kt`)**:
   - Shifted IA focus to pure expenditure tracking and budgeting (without split debts).
   - **Hero Pinned Card #1: Personal Expenses**:
     - Featured at the top of "All Trackers" as the special primary solo tracker.
     - Displays monthly spend, budget limit, and slim progress bar with quick navigation into the detailed Personal Log.
   - **Shared Expense Groups**:
     - Cleanly displayed directly below the Personal card under `SHARED EXPENSE GROUPS`.
     - Displays each tracker group with icon, name, member count, and tap-through to group details.
   - **Personal Log Tab**:
     - Provides the complete detailed personal ledger with search, category filter pills, receipt viewer, and transaction history.

3. **Bottom Navigation Alignment (`HomeScreen.kt`)**:
   - Updated tab 2 label from "Personal" to "Expenses" to cleanly reflect the unified tracking model.

4. **Design System & Zero Emojis**:
   - Strictly 100% monochrome design using `colors.canvasChalk`, `colors.inkPrimary`, `colors.surfaceCard`, and `colors.borderWhisper`.
   - Zero emojis across all modified code, UI strings, dialogs, and toasts.

### Verification & Testing
- `compileDebugKotlin`: SUCCESSFUL (50s)
- `testDebugUnitTest`: SUCCESSFUL (37s, 27 tasks)


## [2026-11-05 13:13] Release v0.4.0.0 Published

### Release Summary
- **Version Code**: 61
- **Version Name**: 0.4.0.0
- **Release Title**: v0.4.0.0 Feature Update - Minimal Split & Expenses Architecture
- **Git Commit**: `d2ac309` on `main` branch
- **GitHub Release**: [v0.4.0.0](https://github.com/inronlbs/SplitSmith/releases/tag/v0.4.0.0)
- **Firebase Hosting CDN**: [https://splitsmith.web.app](https://splitsmith.web.app) (binary: `splitsmith.bin`, metadata: `version.json`)

### Key Features & Architectural Upgrades in v0.4.0.0
1. **Reimagined Split Hub**:
   - Single unified WhatsApp-style feed combining Split Groups and 1-on-1 Split Friends.
   - Minimal filter pills (`All`, `Groups`, `Friends`) with real-time query search across groups and contacts.
   - Minimal standardized rows with JetBrains Mono net balance polarity (`+₹X`, `-₹Y`, `settled`).
2. **Unified Expense Tracking Hub**:
   - Pinned Personal Solo Tracker as the special primary tracker at the top with live budget progress bar.
   - Dedicated Shared Expense Groups section (`SHARED EXPENSE GROUPS`) for non-debt shared tracking.
   - Full Personal Log view with search, categories, and receipt attachment viewer.
3. **Full-Screen Member Picker & Clearance RBAC**:
   - Bottom-expanding full-screen participant sheet with live search, WhatsApp-style staged chips, and batch invites.
   - Member removal RBAC clearance with destructive confirmation dialog for group owners and admins.
4. **Robust Update Mechanism**:
   - Tier 1: High-speed Firebase Hosting CDN metadata check (`https://splitsmith.web.app/version.json`) with zero rate limits.
   - Tier 2: Automatic fallback to GitHub Releases API.
   - Package installer permission helper detecting `canRequestPackageInstalls()` on Android 8.0+.
5. **Zero Emojis & Monochrome Design**:
   - Verified 100% monochrome design system adherence and strictly zero emojis across all code, strings, and dialogs.

### Binary Assets
- **AAB**: `SplitSmith-v0.4.0.0-release.aab` (43.7 MB)
- **APK**: `SplitSmith-v0.4.0.0-release.apk` (70.3 MB)
- **CDN**: `splitsmith.bin` (70.3 MB) deployed to Firebase Hosting


## [2026-11-05 13:28] Removed Redundant Settings Tab & Unified Preset Personal Group

### Changes Implemented
1. **Group Detail Streamlining (`GroupDetailScreen.kt`)**:
   - Removed redundant "Settings" tab from the horizontal swipe pager.
   - Pager reduced from 3 pages to 2 pages (`pageCount = { 2 }`):
     - Split Groups: `Expenses` and `Balances`.
     - Tracker Groups: `Expenses` and `Breakdown`.
   - Dedicated Settings bottom sheet remains fully accessible via the top-bar gear icon (`Icons.Default.Settings`) and tapping the group header.

2. **Preset Personal Group in Expenses Tab (`PersonalExpensesScreen.kt`)**:
   - Removed the top segmented pill switcher (`[ All Trackers | Personal Log ]`).
   - Unified the Expenses tab into a single, clean list of Expense Tracker Groups.
   - Pinned **Preset "Personal" Group** as the first item:
     - Avatar: User's profile avatar.
     - Title: "Personal" with a subtle monochrome "Preset" badge.
     - Caption: "1 participant (You) · ₹X this month".
     - Tapping opens the detailed personal ledger view.
   - Integrated `BackHandler` and a top back button inside the personal ledger view to smoothly return to the groups list.
   - Maintained all shared non-split tracker groups directly below it under `SHARED EXPENSE GROUPS`.

### Verification & Testing
- `compileDebugKotlin`: SUCCESSFUL (28s)
- `testDebugUnitTest`: SUCCESSFUL (22s, 27 tasks passed)
- Zero emojis verified across codebase diff.
