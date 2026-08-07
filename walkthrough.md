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
