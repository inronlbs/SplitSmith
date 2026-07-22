package com.splitsmith.app.data

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val upiId: String = "",
    val customCategories: List<String> = emptyList(),
    val shortCode: String = "",
    val budget: BudgetConfig = BudgetConfig(limit = 15000.0, type = "MONTHLY", threshold = 80)
)

@Serializable
data class BudgetConfig(
    val limit: Double = 0.0,
    val type: String = "TRIP", // TRIP or MONTHLY
    val threshold: Int = 80
)

@Serializable
data class Group(
    val id: String = "",
    val name: String = "",
    val iconName: String = "GroupsOutlined",
    val type: String = "Other", // Trip, Home, Office, Food, Other
    val budget: BudgetConfig = BudgetConfig(),
    val members: Map<String, Boolean> = emptyMap(),
    val adminId: String = "",
    val admins: Map<String, Boolean> = emptyMap(),
    val joinRequests: Map<String, Boolean> = emptyMap(),
    val customCategories: List<String> = emptyList()
)

@Serializable
data class Expense(
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: Long = 0,
    val paidBy: String = "",
    val category: String = "Other", // Food, Travel, Stay, Utilities, Movie, Shopping, Other
    val splitMode: String = "EQUAL", // EQUAL, EXACT, PERCENTAGE, SHARES
    val splits: Map<String, Double> = emptyMap(), // uid -> amount
    val receiptUrl: String = "",
    val createdBy: String = ""
)

@Serializable
data class Settlement(
    val id: String = "",
    val fromUser: String = "",
    val toUser: String = "",
    val amount: Double = 0.0,
    val method: String = "CASH", // UPI or CASH
    val status: String = "PENDING", // PENDING or CONFIRMED
    val upiRef: String = "",
    val timestamp: Long = 0
)

@Serializable
data class PersonalExpense(
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val category: String = "Other",
    val note: String = "",
    val date: Long = 0
)

@Serializable
data class DirectSplit(
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paidBy: String = "",           // uid who paid
    val withUser: String = "",         // the other uid
    val myShare: Double = 0.0,         // amount the other person owes paidBy (if positive, they owe us; if negative, we owe them)
    val category: String = "Other",
    val status: String = "PENDING",    // PENDING, WAITING_APPROVAL, or SETTLED
    val method: String = "UPI",        // UPI or CASH
    val date: Long = 0,
    val createdBy: String = ""
)

data class Debt(
    val fromUser: String,
    val toUser: String,
    val amount: Double
)

