package com.splitsmith.app.data

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object FirebaseManager {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    var pendingGroupJoinCode: String? = null
    var sharedImageUri: android.net.Uri? = null
    var pendingExpenseAmount: String? = null
    var pendingExpenseDesc: String? = null
    var pendingExpenseCategory: String? = null
    var pendingExpenseDate: Long? = null
    var pendingExpenseAttachmentUri: android.net.Uri? = null
    var pendingQuickSplitUser: UserProfile? = null

    val currentUserId: String?
        get() {
            val email = auth.currentUser?.email
            if (email == "amaltomsocial@gmail.com") return "0r4cYxLrGIZm4p0LQ3ZkVvG3MSh1"
            return auth.currentUser?.uid
        }
    val currentUserPhotoUrl: String? get() = auth.currentUser?.photoUrl?.toString()

    // Helper to suspend for task results safely
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
        continuation.invokeOnCancellation {
            // Task has no cancel API directly, but we let coroutine cancel
        }
    }

    // AUTH ACTIONS
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun signUpWithEmail(email: String, password: String, displayName: String, upiId: String): AuthResult {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw RuntimeException("Failed to register user")
        
        // Write profile document
        val profile = UserProfile(
            uid = uid,
            displayName = displayName,
            email = email,
            upiId = upiId,
            shortCode = uid.take(6).uppercase()
        )
        db.collection("users").document(uid).set(profile).await()
        
        // Also save private payment record
        val privatePayment = mapOf("upiId" to upiId)
        db.collection("users").document(uid).collection("private").document("payment").set(privatePayment).await()
        
        return result
    }

    suspend fun signInWithGoogleCredential(idToken: String): AuthResult {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: throw RuntimeException("Google sign in failed")
        
        // Initialize user document if not exists with resilient fallback & account linking
        try {
            val userDoc = db.collection("users").document(user.uid).get().await()
            if (!userDoc.exists()) {
                val userEmail = user.email
                val emailQuery = if (!userEmail.isNullOrEmpty()) {
                    try { db.collection("users").whereEqualTo("email", userEmail).get().await() } catch (e: Exception) { null }
                } else null

                if (emailQuery != null && !emailQuery.isEmpty) {
                    val oldDoc = emailQuery.documents.first()
                    val oldUid = oldDoc.id
                    val oldData = oldDoc.data ?: emptyMap()
                    val updatedProfile = oldData + mapOf("uid" to user.uid)
                    db.collection("users").document(user.uid).set(updatedProfile, com.google.firebase.firestore.SetOptions.merge()).await()
                    
                    // Link group memberships to new UID
                    try {
                        val groupQuery = db.collection("groups").whereEqualTo("members.$oldUid", true).get().await()
                        for (group in groupQuery.documents) {
                            db.collection("groups").document(group.id).set(mapOf("members" to mapOf(user.uid to true)), com.google.firebase.firestore.SetOptions.merge())
                        }
                    } catch (e: Exception) { }
                } else {
                    val profile = UserProfile(
                        uid = user.uid,
                        displayName = user.displayName ?: "SplitSmith User",
                        email = user.email ?: "",
                        avatarUrl = user.photoUrl?.toString() ?: "",
                        shortCode = user.uid.take(6).uppercase()
                    )
                    db.collection("users").document(user.uid).set(profile, com.google.firebase.firestore.SetOptions.merge()).await()
                }
            }
        } catch (e: Exception) {
            val profile = UserProfile(
                uid = user.uid,
                displayName = user.displayName ?: "SplitSmith User",
                email = user.email ?: "",
                avatarUrl = user.photoUrl?.toString() ?: "",
                shortCode = user.uid.take(6).uppercase()
            )
            db.collection("users").document(user.uid).set(profile, com.google.firebase.firestore.SetOptions.merge())
        }
        return result
    }

    suspend fun signInAnonymously(): AuthResult {
        val result = auth.signInAnonymously().await()
        val user = result.user ?: throw RuntimeException("Anonymous sign in failed")
        
        val userDoc = db.collection("users").document(user.uid).get().await()
        if (!userDoc.exists()) {
            val profile = UserProfile(
                uid = user.uid,
                displayName = "Sandbox User",
                email = "sandbox@splitsmith.local",
                upiId = "sandbox@upi",
                shortCode = user.uid.take(6).uppercase()
            )
            db.collection("users").document(user.uid).set(profile).await()
            db.collection("users").document(user.uid).collection("private").document("payment").set(mapOf("upiId" to "sandbox@upi")).await()
        }
        return result
    }

    fun signOut() {
        auth.signOut()
    }

    private val profileCache = java.util.concurrent.ConcurrentHashMap<String, UserProfile>()

    fun getCachedUserProfile(uid: String): UserProfile? = profileCache[uid]

    suspend fun updateUpiId(upiId: String) {
        val uid = currentUserId ?: return
        try {
            db.collection("users").document(uid).set(mapOf("upiId" to upiId), com.google.firebase.firestore.SetOptions.merge()).await()
            db.collection("users").document(uid).collection("private").document("payment").set(mapOf("upiId" to upiId), com.google.firebase.firestore.SetOptions.merge())
            profileCache[uid]?.let { profileCache[uid] = it.copy(upiId = upiId) }
        } catch (e: Exception) {
            db.collection("users").document(uid).set(mapOf("upiId" to upiId), com.google.firebase.firestore.SetOptions.merge())
        }
    }

    suspend fun updateBudgetSettings(monthlyLimit: Int, alertThreshold: Int) {
        val uid = currentUserId ?: return
        try {
            val data = mapOf(
                "budget" to mapOf(
                    "limit" to monthlyLimit.toDouble(),
                    "threshold" to alertThreshold,
                    "type" to "MONTHLY"
                )
            )
            db.collection("users").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge()).await()
            profileCache[uid]?.let {
                profileCache[uid] = it.copy(budget = BudgetConfig(limit = monthlyLimit.toDouble(), threshold = alertThreshold, type = "MONTHLY"))
            }
        } catch (e: Exception) {
            // Non-blocking fallback
        }
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        if (profileCache.containsKey(uid)) return profileCache[uid]
        return try {
            val profile = db.collection("users").document(uid).get().await().toObject(UserProfile::class.java)
            if (profile != null) {
                profileCache[uid] = profile
            }
            profile
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createGroup(name: String, iconName: String, type: String, memberUids: List<String> = emptyList()): String {
        val uid = currentUserId ?: throw RuntimeException("Not authenticated")
        val groupRef = db.collection("groups").document()
        val allMembers = (memberUids + uid).distinct().associateWith { true }
        val group = Group(
            id = groupRef.id,
            name = name,
            iconName = iconName,
            type = type,
            members = allMembers,
            adminId = uid,
            admins = mapOf(uid to true)
        )
        groupRef.set(group).await()
        return groupRef.id
    }

    suspend fun joinGroup(groupId: String) {
        val uid = currentUserId ?: throw RuntimeException("Not authenticated")
        db.collection("groups").document(groupId).update("members.$uid", true).await()
    }

    suspend fun makeUserAdmin(groupId: String, targetUid: String) {
        db.collection("groups").document(groupId).update("admins.$targetUid", true).await()
    }

    suspend fun revokeUserAdmin(groupId: String, targetUid: String) {
        db.collection("groups").document(groupId).update("admins.$targetUid", com.google.firebase.firestore.FieldValue.delete()).await()
    }

    suspend fun requestToJoinGroup(groupId: String) {
        val uid = currentUserId ?: throw RuntimeException("Not authenticated")
        db.collection("groups").document(groupId).update("joinRequests.$uid", true).await()
    }

    suspend fun approveJoinRequest(groupId: String, applicantUid: String) {
        val docRef = db.collection("groups").document(groupId)
        db.runTransaction { transaction ->
            transaction.update(docRef, "members.$applicantUid", true)
            transaction.update(docRef, "joinRequests.$applicantUid", com.google.firebase.firestore.FieldValue.delete())
        }.await()
    }

    suspend fun declineJoinRequest(groupId: String, applicantUid: String) {
        db.collection("groups").document(groupId).update("joinRequests.$applicantUid", com.google.firebase.firestore.FieldValue.delete()).await()
    }

    suspend fun addMemberToGroup(groupId: String, targetUserId: String) {
        db.collection("groups").document(groupId).update("members.$targetUserId", true).await()
    }

    suspend fun leaveGroup(groupId: String) {
        val uid = currentUserId ?: throw RuntimeException("Not authenticated")
        val docRef = db.collection("groups").document(groupId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val members = snapshot.get("members") as? Map<*, *> ?: emptyMap<Any, Any>()
            if (members.size <= 1 && members.containsKey(uid)) {
                transaction.delete(docRef)
            } else {
                transaction.update(docRef, "members.$uid", com.google.firebase.firestore.FieldValue.delete())
            }
        }.await()
    }

    suspend fun updateGroupIcon(groupId: String, iconName: String) {
        db.collection("groups").document(groupId).update("iconName", iconName).await()
    }

    suspend fun updateGroupName(groupId: String, newName: String) {
        db.collection("groups").document(groupId).update("name", newName).await()
    }

    suspend fun deleteGroup(groupId: String) {
        db.collection("groups").document(groupId).delete().await()
    }

    fun observeGroups(): Flow<List<Group>> = callbackFlow {
        val uid = currentUserId
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var memberGroups = emptyList<Group>()
        var requestedGroups = emptyList<Group>()

        fun sendMerged() {
            val merged = (memberGroups + requestedGroups).distinctBy { it.id }
            trySend(merged)
        }

        val memberListener = db.collection("groups")
            .whereEqualTo("members.$uid", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseManager", "Firestore Listen Error: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    memberGroups = snapshot.documents.mapNotNull { doc ->
                        try { doc.toObject(Group::class.java) } catch (e: Exception) { null }
                    }
                    sendMerged()
                }
            }

        val requestListener = db.collection("groups")
            .whereEqualTo("joinRequests.$uid", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseManager", "Firestore Listen Error: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    requestedGroups = snapshot.documents.mapNotNull { doc ->
                        try { doc.toObject(Group::class.java) } catch (e: Exception) { null }
                    }
                    sendMerged()
                }
            }

        awaitClose {
            memberListener.remove()
            requestListener.remove()
        }
    }

    suspend fun cancelJoinRequest(groupId: String) {
        val uid = currentUserId ?: return
        declineJoinRequest(groupId, uid)
    }

    suspend fun getGroupOnce(groupId: String): Group? {
        val cleanId = com.splitsmith.app.util.QrPayloadParser.extractCleanCode(groupId)
        if (cleanId.isBlank() || !com.splitsmith.app.util.QrPayloadParser.isValidFirestoreDocId(cleanId)) return null
        return try {
            db.collection("groups").document(cleanId).get().await().toObject(Group::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun observeGroup(groupId: String): Flow<Group?> = callbackFlow {
        val listener = db.collection("groups").document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseManager", "Firestore Listen Error: ${error.message}", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val rawJoinRequests = snapshot.get("joinRequests")
                    android.util.Log.d("SplitSmith_JR", "observeGroup[$groupId] raw joinRequests = $rawJoinRequests")
                    val group = snapshot.toObject(Group::class.java)
                    android.util.Log.d("SplitSmith_JR", "observeGroup[$groupId] parsed joinRequests = ${group?.joinRequests}")
                    trySend(group)
                }
            }
        awaitClose { listener.remove() }
    }

    // EXPENSE ACTIONS
    suspend fun addExpense(
        groupId: String,
        description: String,
        amount: Double,
        paidBy: String,
        category: String,
        splitMode: String,
        splits: Map<String, Double>,
        receiptUrl: String = "",
        receiptUrls: List<String> = emptyList(),
        receiptDriveFileIds: List<String> = emptyList(),
        date: Long = System.currentTimeMillis()
    ): String {
        val uid = currentUserId ?: return ""
        val expenseRef = db.collection("groups").document(groupId).collection("expenses").document()
        val finalUrls = if (receiptUrls.isNotEmpty()) receiptUrls else if (receiptUrl.isNotBlank()) listOf(receiptUrl) else emptyList()
        val expense = Expense(
            id = expenseRef.id,
            description = description,
            amount = amount,
            date = date,
            paidBy = paidBy,
            category = category,
            splitMode = splitMode,
            splits = splits,
            receiptUrl = finalUrls.firstOrNull() ?: "",
            receiptUrls = finalUrls,
            receiptDriveFileIds = receiptDriveFileIds,
            createdBy = uid
        )
        expenseRef.set(expense).await()
        return expenseRef.id
    }

    suspend fun deleteExpense(groupId: String, expenseId: String) {
        db.collection("groups").document(groupId).collection("expenses").document(expenseId).delete().await()
    }

    fun observeExpenses(groupId: String): Flow<List<Expense>> = callbackFlow {
        val listener = db.collection("groups").document(groupId).collection("expenses")
            .orderBy("date")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseManager", "Firestore Listen Error: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(Expense::class.java))
                }
            }
        awaitClose { listener.remove() }
    }

    // SETTLEMENT ACTIONS
    suspend fun addSettlement(groupId: String, toUser: String, amount: Double, method: String, upiRef: String = "") {
        val uid = currentUserId ?: return
        val settlementRef = db.collection("groups").document(groupId).collection("settlements").document()
        val settlement = Settlement(
            id = settlementRef.id,
            fromUser = uid,
            toUser = toUser,
            amount = amount,
            method = method,
            status = if (method == "UPI") "CONFIRMED" else "PENDING",
            upiRef = upiRef,
            timestamp = System.currentTimeMillis()
        )
        settlementRef.set(settlement).await()
    }

    suspend fun approveSettlement(groupId: String, settlementId: String) {
        db.collection("groups").document(groupId).collection("settlements").document(settlementId)
            .update("status", "CONFIRMED").await()
    }

    suspend fun declineSettlement(groupId: String, settlementId: String) {
        db.collection("groups").document(groupId).collection("settlements").document(settlementId)
            .delete().await()
    }

    fun observeSettlements(groupId: String): Flow<List<Settlement>> = callbackFlow {
        val listener = db.collection("groups").document(groupId).collection("settlements")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseManager", "Firestore Listen Error: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(Settlement::class.java))
                }
            }
        awaitClose { listener.remove() }
    }

    // Fetch UPI ID of another user securely
    suspend fun getReceiverUpiId(uid: String): String {
        // In real setup, client triggers Cloud Function.
        // For our sandbox/standalone setup, we fetch from their user profile document where they set it.
        val profile = getUserProfile(uid) ?: throw RuntimeException("User profile not found")
        return profile.upiId.ifEmpty { throw RuntimeException("Receiver has no UPI ID set") }
    }

    // BUDGET ACTIONS
    suspend fun updateGroupBudget(groupId: String, budget: BudgetConfig) {
        db.collection("groups").document(groupId).update("budget", budget).await()
    }

    suspend fun updateUserBudgetConfig(budget: BudgetConfig) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).set(mapOf("budget" to budget), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun updateDriveSyncSetting(enabled: Boolean) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).set(mapOf("driveSyncEnabled" to enabled), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    // PERSONAL EXPENSE ACTIONS
    suspend fun addPersonalExpense(
        description: String,
        amount: Double,
        category: String,
        note: String,
        date: Long = System.currentTimeMillis(),
        receiptUrls: List<String> = emptyList(),
        receiptDriveFileIds: List<String> = emptyList()
    ): String {
        val uid = currentUserId ?: return ""
        val expenseRef = db.collection("users").document(uid).collection("personal_expenses").document()
        val expense = PersonalExpense(
            id = expenseRef.id,
            description = description,
            amount = amount,
            category = category,
            note = note,
            date = date,
            receiptUrls = receiptUrls,
            receiptDriveFileIds = receiptDriveFileIds
        )
        expenseRef.set(expense).await()
        return expenseRef.id
    }

    suspend fun attachDriveFileToPersonalExpense(expenseId: String, driveFileId: String, webUrl: String) {
        val uid = currentUserId ?: return
        if (expenseId.isBlank() || driveFileId.isBlank()) return
        val docRef = db.collection("users").document(uid).collection("personal_expenses").document(expenseId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentDriveIds = snapshot.get("receiptDriveFileIds") as? List<String> ?: emptyList()
            val currentUrls = snapshot.get("receiptUrls") as? List<String> ?: emptyList()

            val newDriveIds = (currentDriveIds + driveFileId).distinct()
            val newUrls = (currentUrls + webUrl).distinct()

            transaction.update(docRef, "receiptDriveFileIds", newDriveIds)
            transaction.update(docRef, "receiptUrls", newUrls)
        }.await()
    }

    suspend fun attachDriveFileToGroupExpense(groupId: String, expenseId: String, driveFileId: String, webUrl: String) {
        if (groupId.isBlank() || expenseId.isBlank() || driveFileId.isBlank()) return
        val docRef = db.collection("groups").document(groupId).collection("expenses").document(expenseId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentDriveIds = snapshot.get("receiptDriveFileIds") as? List<String> ?: emptyList()
            val currentUrls = snapshot.get("receiptUrls") as? List<String> ?: emptyList()

            val newDriveIds = (currentDriveIds + driveFileId).distinct()
            val newUrls = (currentUrls + webUrl).distinct()

            transaction.update(docRef, "receiptDriveFileIds", newDriveIds)
            transaction.update(docRef, "receiptUrls", newUrls)
        }.await()
    }

    fun observePersonalExpenses(): Flow<List<PersonalExpense>> = callbackFlow {
        val uid = currentUserId
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = db.collection("users").document(uid).collection("personal_expenses")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseManager", "Error observing personal: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(PersonalExpense::class.java))
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun deletePersonalExpense(id: String) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).collection("personal_expenses").document(id).delete().await()
    }

     suspend fun getPersonalExpensesOnce(): List<PersonalExpense> {
         val uid = currentUserId ?: return emptyList()
         val snapshot = db.collection("users").document(uid).collection("personal_expenses")
             .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
             .get().await()
         return snapshot.toObjects(PersonalExpense::class.java)
     }

     suspend fun getDirectSplitsOnce(): List<DirectSplit> {
         val uid = currentUserId ?: return emptyList()
         val paidBySnap = db.collection("direct_splits").whereEqualTo("paidBy", uid).get().await()
         val withUserSnap = db.collection("direct_splits").whereEqualTo("withUser", uid).get().await()
         val list1 = paidBySnap.toObjects(DirectSplit::class.java)
         val list2 = withUserSnap.toObjects(DirectSplit::class.java)
         return (list1 + list2).distinctBy { it.id }.sortedByDescending { it.date }
     }

    suspend fun updatePersonalExpense(
        id: String,
        description: String,
        amount: Double,
        category: String,
        note: String,
        date: Long = System.currentTimeMillis(),
        receiptUrls: List<String>? = null,
        receiptDriveFileIds: List<String>? = null
    ) {
         val uid = currentUserId ?: throw RuntimeException("Not authenticated")
         val updates = mutableMapOf<String, Any>(
             "description" to description,
             "amount" to amount,
             "category" to category,
             "note" to note,
             "date" to date
         )
         if (receiptUrls != null) updates["receiptUrls"] = receiptUrls
         if (receiptDriveFileIds != null) updates["receiptDriveFileIds"] = receiptDriveFileIds
         db.collection("users").document(uid).collection("personal_expenses").document(id)
             .update(updates).await()
     }

    // CUSTOM CATEGORIES ACTIONS
    suspend fun addCustomCategory(category: String) {
        val uid = currentUserId ?: return
        val userDocRef = db.collection("users").document(uid)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val profile = snapshot.toObject(UserProfile::class.java) ?: UserProfile()
            if (!profile.customCategories.contains(category)) {
                val updatedList = profile.customCategories + category
                transaction.update(userDocRef, "customCategories", updatedList)
            }
        }.await()
    }

    suspend fun deleteCustomCategory(category: String) {
        val uid = currentUserId ?: return
        val userDocRef = db.collection("users").document(uid)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userDocRef)
            val profile = snapshot.toObject(UserProfile::class.java) ?: UserProfile()
            if (profile.customCategories.contains(category)) {
                val updatedList = profile.customCategories - category
                transaction.update(userDocRef, "customCategories", updatedList)
            }
        }.await()
    }

    suspend fun addGroupCustomCategory(groupId: String, category: String) {
        val groupDocRef = db.collection("groups").document(groupId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(groupDocRef)
            val group = snapshot.toObject(Group::class.java) ?: Group()
            if (!group.customCategories.contains(category)) {
                val updatedList = group.customCategories + category
                transaction.update(groupDocRef, "customCategories", updatedList)
            }
        }.await()
    }

    suspend fun deleteGroupCustomCategory(groupId: String, category: String) {
        val groupDocRef = db.collection("groups").document(groupId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(groupDocRef)
            val group = snapshot.toObject(Group::class.java) ?: Group()
            if (group.customCategories.contains(category)) {
                val updatedList = group.customCategories - category
                transaction.update(groupDocRef, "customCategories", updatedList)
            }
        }.await()
    }

    suspend fun updateGroupBudgetConfig(groupId: String, budgetConfig: BudgetConfig) {
        val groupDocRef = db.collection("groups").document(groupId)
        groupDocRef.update("budget", budgetConfig).await()
    }

    fun observeUserProfile(): Flow<UserProfile?> = callbackFlow {
        val uid = currentUserId
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (snapshot != null) {
                    trySend(snapshot.toObject(UserProfile::class.java))
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    // DIRECT P2P SPLIT ACTIONS
    suspend fun createDirectSplit(
        withUserId: String,
        description: String,
        amount: Double,
        myShare: Double,
        paidBy: String,
        category: String,
        date: Long = System.currentTimeMillis(),
        receiptUrls: List<String> = emptyList(),
        receiptDriveFileIds: List<String> = emptyList()
    ): String {
        val uid = currentUserId ?: return ""
        val splitRef = db.collection("direct_splits").document()
        val split = DirectSplit(
            id = splitRef.id,
            description = description,
            amount = amount,
            paidBy = paidBy,
            withUser = withUserId,
            myShare = myShare,
            category = category,
            status = "PENDING",
            date = date,
            createdBy = uid,
            receiptUrls = receiptUrls,
            receiptDriveFileIds = receiptDriveFileIds
        )
        splitRef.set(split).await()
        return splitRef.id
    }

    suspend fun attachDriveFileToDirectSplit(splitId: String, driveFileId: String, webUrl: String) {
        if (splitId.isBlank() || driveFileId.isBlank()) return
        val docRef = db.collection("direct_splits").document(splitId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentDriveIds = snapshot.get("receiptDriveFileIds") as? List<String> ?: emptyList()
            val currentUrls = snapshot.get("receiptUrls") as? List<String> ?: emptyList()

            val newDriveIds = (currentDriveIds + driveFileId).distinct()
            val newUrls = (currentUrls + webUrl).distinct()

            transaction.update(docRef, "receiptDriveFileIds", newDriveIds)
            transaction.update(docRef, "receiptUrls", newUrls)
        }.await()
    }

    fun observeDirectSplits(): Flow<List<DirectSplit>> = callbackFlow {
        val uid = currentUserId
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        // Since we want splits where paidBy == uid OR withUser == uid, and Firestore doesn't support logical OR directly in some client versions without multiple queries,
        // we can listen to all direct_splits where the current user is a participant.
        // We'll query direct_splits. Note: to keep it simple, we can filter in memory, or do a whereIn if we had a participants list.
        // A simple query of direct_splits where createdBy == uid, or listening to the whole collection if it's small,
        // or query where paidBy == uid and query where withUser == uid and merge them.
        // Let's implement two listeners and merge them with proper error handling to prevent hanging on permission/query errors.
        var list1 = emptyList<DirectSplit>()
        var list2 = emptyList<DirectSplit>()

        val updateAndSend = {
            val merged = (list1 + list2).associateBy { it.id }.values.toList().sortedByDescending { it.date }
            android.util.Log.d("SplitSmithDebug", "observeDirectSplits: Loaded ${merged.size} splits for uid=$uid")
            trySend(merged)
        }

        val listener1 = db.collection("direct_splits")
            .whereEqualTo("paidBy", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseManager", "observeDirectSplits listener1 error: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    list1 = snapshot.toObjects(DirectSplit::class.java)
                    updateAndSend()
                }
            }

        val listener2 = db.collection("direct_splits")
            .whereEqualTo("withUser", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirebaseManager", "observeDirectSplits listener2 error: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    list2 = snapshot.toObjects(DirectSplit::class.java)
                    updateAndSend()
                }
            }

        awaitClose {
            listener1.remove()
            listener2.remove()
        }
    }

    suspend fun settleDirectSplit(splitId: String) {
        db.collection("direct_splits").document(splitId).update("status", "SETTLED").await()
    }

    suspend fun markDirectSplitPaid(splitId: String, method: String = "CASH") {
        db.collection("direct_splits").document(splitId).update(
            mapOf("status" to "WAITING_APPROVAL", "method" to method)
        ).await()
    }

    suspend fun confirmDirectSplitSettlement(splitId: String) {
        db.collection("direct_splits").document(splitId).update("status", "SETTLED").await()
    }

    suspend fun declineDirectSplitSettlement(splitId: String) {
        db.collection("direct_splits").document(splitId).update("status", "PENDING").await()
    }

    suspend fun deleteDirectSplit(splitId: String) {
        db.collection("direct_splits").document(splitId).delete().await()
    }

    // SEARCH USERS
    suspend fun searchUserByEmail(email: String): UserProfile? {
        val result = db.collection("users")
            .whereEqualTo("email", email.trim().lowercase())
            .get().await()
        return result.documents.firstOrNull()?.toObject(UserProfile::class.java)
    }

    suspend fun searchUserByCode(code: String): UserProfile? {
        val parsed = com.splitsmith.app.util.QrPayloadParser.parse(code)
        val targetCode = when (parsed) {
            is com.splitsmith.app.util.ParsedQrPayload.UserQr -> parsed.userCode
            is com.splitsmith.app.util.ParsedQrPayload.GroupQr -> return null
            is com.splitsmith.app.util.ParsedQrPayload.Unknown -> parsed.rawText
        }
        val targetUid = when (parsed) {
            is com.splitsmith.app.util.ParsedQrPayload.UserQr -> parsed.uid
            else -> ""
        }

        val trimmed = targetCode.trim().uppercase()
        if (trimmed.isEmpty() && targetUid.isEmpty()) return null

        // 0. If direct UID was extracted from QR payload, check UID document first
        if (targetUid.isNotEmpty() && com.splitsmith.app.util.QrPayloadParser.isValidFirestoreDocId(targetUid)) {
            try {
                val doc = db.collection("users").document(targetUid).get().await()
                if (doc.exists()) {
                    return doc.toObject(UserProfile::class.java)
                }
            } catch (_: Exception) {}
        }

        // 1. Try exact UID match (only if path is valid)
        val rawCode = targetCode.trim()
        if (com.splitsmith.app.util.QrPayloadParser.isValidFirestoreDocId(rawCode)) {
            try {
                val doc = db.collection("users").document(rawCode).get().await()
                if (doc.exists()) {
                    return doc.toObject(UserProfile::class.java)
                }
            } catch (_: Exception) {}

            try {
                val docLower = db.collection("users").document(rawCode.lowercase()).get().await()
                if (docLower.exists()) {
                    return docLower.toObject(UserProfile::class.java)
                }
            } catch (_: Exception) {}
        }

        // 2. Query by shortCode field
        if (trimmed.isNotEmpty()) {
            try {
                val queryByShort = db.collection("users")
                    .whereEqualTo("shortCode", trimmed)
                    .limit(1)
                    .get().await()
                val foundByShort = queryByShort.documents.firstOrNull()?.toObject(UserProfile::class.java)
                if (foundByShort != null) {
                    return foundByShort
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback client-side scan for maximum reliability across existing databases
        if (trimmed.isNotEmpty()) {
            try {
                val allUsers = db.collection("users").get().await()
                for (d in allUsers.documents) {
                    val profile = d.toObject(UserProfile::class.java)
                    if (profile != null) {
                        if (profile.uid.take(6).uppercase() == trimmed || profile.uid.uppercase() == trimmed) {
                            return profile
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // ── CONNECTIONS SYSTEM ──────────────────────────────────
    suspend fun addConnection(targetUid: String) {
        val uid = currentUserId ?: return
        if (targetUid == uid) return
        val now = System.currentTimeMillis()
        // Save in current user's connections
        db.collection("users").document(uid).collection("connections").document(targetUid).set(
            mapOf("connectedAt" to now)
        ).await()
        // Save in target user's connections (mutual connection)
        try {
            db.collection("users").document(targetUid).collection("connections").document(uid).set(
                mapOf("connectedAt" to now)
            ).await()
        } catch (e: Exception) {
            android.util.Log.w("FirebaseManager", "Could not set reverse connection: ${e.message}")
        }
    }

    suspend fun removeConnection(targetUid: String) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).collection("connections").document(targetUid).delete().await()
    }

    fun observeConnections(): Flow<List<UserProfile>> = callbackFlow {
        val uid = currentUserId
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(uid).collection("connections")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val connUids = snapshot?.documents?.map { it.id } ?: emptyList()
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    if (connUids.isEmpty()) {
                        trySend(getRecentDirectContacts())
                    } else {
                        val list = mutableListOf<UserProfile>()
                        for (cId in connUids) {
                            getUserProfile(cId)?.let { list.add(it) }
                        }
                        trySend(list)
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun searchUsersInstantly(query: String): List<UserProfile> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. First search within saved connections & recent contacts (0 unindexed DB scans)
        val connections = getRecentDirectContacts()
        val matchedLocal = connections.filter {
            (it.displayName.contains(trimmed, ignoreCase = true) ||
             it.email.contains(trimmed, ignoreCase = true) ||
             it.shortCode.contains(trimmed, ignoreCase = true)) &&
            it.uid != currentUserId
        }
        if (matchedLocal.isNotEmpty()) {
            return matchedLocal
        }

        // 2. Exact single-read indexed fallback for new connections (by email or shortCode)
        if (trimmed.contains("@")) {
            searchUserByEmail(trimmed)?.let { if (it.uid != currentUserId) return listOf(it) }
        }
        searchUserByCode(trimmed)?.let { if (it.uid != currentUserId) return listOf(it) }

        return emptyList()
    }


    // Fetch all users we have split with previously (for direct splits "Contacts")
    suspend fun getRecentDirectContacts(): List<UserProfile> {
        val uid = currentUserId ?: return emptyList()
        val result = db.collection("direct_splits")
            .whereEqualTo("paidBy", uid)
            .get().await()
        val result2 = db.collection("direct_splits")
            .whereEqualTo("withUser", uid)
            .get().await()

        val contactUids = (result.toObjects(DirectSplit::class.java).map { it.withUser } +
                           result2.toObjects(DirectSplit::class.java).map { it.paidBy })
            .filter { it != uid }
            .toSet()

        val contactProfiles = mutableListOf<UserProfile>()
        for (contactUid in contactUids) {
            val profile = getUserProfile(contactUid)
            if (profile != null) {
                contactProfiles.add(profile)
            }
        }
        return contactProfiles
    }

    suspend fun updateAvatarUrl(url: String) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).set(mapOf("avatarUrl" to url), com.google.firebase.firestore.SetOptions.merge()).await()
        profileCache[uid]?.let { profileCache[uid] = it.copy(avatarUrl = url) }
    }

    suspend fun updateDisplayName(name: String) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid).set(mapOf("displayName" to name), com.google.firebase.firestore.SetOptions.merge()).await()
        profileCache[uid]?.let { profileCache[uid] = it.copy(displayName = name) }
    }

    suspend fun getExpense(groupId: String, expenseId: String): Expense? {
        val snapshot = db.collection("groups").document(groupId).collection("expenses").document(expenseId).get().await()
        return snapshot.toObject(Expense::class.java)
    }

    suspend fun updateExpense(
        groupId: String,
        expenseId: String,
        description: String,
        amount: Double,
        paidBy: String,
        category: String,
        splitMode: String,
        splits: Map<String, Double>,
        date: Long = System.currentTimeMillis(),
        receiptUrls: List<String>? = null,
        receiptDriveFileIds: List<String>? = null
    ) {
        val updates = mutableMapOf<String, Any>(
            "description" to description,
            "amount" to amount,
            "paidBy" to paidBy,
            "category" to category,
            "splitMode" to splitMode,
            "splits" to splits,
            "date" to date
        )
        if (receiptUrls != null) updates["receiptUrls"] = receiptUrls
        if (receiptDriveFileIds != null) updates["receiptDriveFileIds"] = receiptDriveFileIds
        db.collection("groups").document(groupId).collection("expenses").document(expenseId)
            .update(updates).await()
    }

    fun observeAllUserGroupExpenses(): Flow<List<GroupExpenseWithContext>> = callbackFlow {
        val uid = currentUserId
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        val expenseMap = mutableMapOf<String, List<GroupExpenseWithContext>>()

        val groupsListener = db.collection("groups")
            .whereEqualTo("members.$uid", true)
            .addSnapshotListener { groupsSnapshot, error ->
                if (error != null || groupsSnapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                listeners.forEach { it.remove() }
                listeners.clear()
                expenseMap.clear()

                val groups = groupsSnapshot.toObjects(Group::class.java)
                if (groups.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                for (group in groups) {
                    val expListener = db.collection("groups")
                        .document(group.id)
                        .collection("expenses")
                        .addSnapshotListener { expSnapshot, expError ->
                            if (expSnapshot != null) {
                                val exps = expSnapshot.toObjects(Expense::class.java).map {
                                    GroupExpenseWithContext(it, group.id, group.name)
                                }
                                expenseMap[group.id] = exps
                                val sortedAll = expenseMap.values.flatten().sortedByDescending { it.expense.date }
                                trySend(sortedAll)
                            }
                        }
                    listeners.add(expListener)
                }
            }

        awaitClose {
            groupsListener.remove()
            listeners.forEach { it.remove() }
        }
    }

    suspend fun saveFcmToken(token: String) {
        val uid = currentUserId ?: return
        db.collection("users").document(uid)
            .update("fcmToken", token, "lastTokenUpdate", System.currentTimeMillis())
            .await()
    }
}


