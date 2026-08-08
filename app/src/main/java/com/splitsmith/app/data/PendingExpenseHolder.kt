package com.splitsmith.app.data

import android.net.Uri

/**
 * Thread-safe single-use holder for transient split data passing across screens.
 * Prevents memory leaks and cross-session state bleed.
 */
object PendingExpenseHolder {

    var pendingGroupJoinCode: String? = null
    var sharedImageUri: Uri? = null

    var pendingExpenseAmount: String? = null
    var pendingExpenseDesc: String? = null
    var pendingExpenseCategory: String? = null
    var pendingExpenseDate: Long? = null
    var pendingExpenseAttachmentUri: Uri? = null
    var pendingExpenseReceiptUrls: List<String>? = null
    var pendingQuickSplitUser: UserProfile? = null
    var pendingConvertedPersonalExpenseId: String? = null

    fun clearAll() {
        pendingGroupJoinCode = null
        sharedImageUri = null
        pendingExpenseAmount = null
        pendingExpenseDesc = null
        pendingExpenseCategory = null
        pendingExpenseDate = null
        pendingExpenseAttachmentUri = null
        pendingExpenseReceiptUrls = null
        pendingQuickSplitUser = null
        pendingConvertedPersonalExpenseId = null
    }
}
