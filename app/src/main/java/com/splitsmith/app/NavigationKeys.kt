package com.splitsmith.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Auth : NavKey

@Serializable
data object Onboarding : NavKey

@Serializable
data object Home : NavKey

@Serializable
data class GroupDetail(val groupId: String) : NavKey

@Serializable
data class AddExpense(val groupId: String, val expenseId: String? = null) : NavKey

@Serializable
data object SplitDetail : NavKey

@Serializable
data object QuickSplit : NavKey

@Serializable
data object QRCode : NavKey

@Serializable
data object ReportsScreenKey : NavKey

@Serializable
data class SlipImportKey(val imageUriStr: String) : NavKey

