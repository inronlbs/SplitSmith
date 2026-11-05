package com.splitsmith.app.ui.home

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.splitsmith.app.data.DirectSplit
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.data.GroupExpenseWithContext
import com.splitsmith.app.data.PersonalExpense
import com.splitsmith.app.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class HomeDashboardUiState(
    val isLoading: Boolean = true,
    val userProfile: UserProfile? = null,
    val isUploadingAvatar: Boolean = false,
    val netPosition: Double = 0.0,
    val totalDirectReceivable: Double = 0.0,
    val totalDirectPayable: Double = 0.0,
    val totalPersonalSpent: Double = 0.0,
    val budgetLimit: Double = 15000.0,
    val budgetProgress: Float = 0f,
    val remainingBudget: Double = 15000.0,
    val dailyAllowance: Double = 0.0,
    val daysRemaining: Int = 1,
    val pendingSettlementApproval: DirectSplit? = null,
    val currentWeekDaySpends: List<Double> = List(7) { 0.0 },
    val currentDayOfWeekIdx: Int = 0,
    val categoryBreakdown: List<Pair<String, Double>> = emptyList(),
    val combinedRecentActivities: List<RecentActivityItem> = emptyList(),
    val curatedActivities: List<RecentActivityItem> = emptyList(),
    val weekTotalSpend: Double = 0.0,
    val maxSpend: Double = 100.0,
    val dailyBudgetBenchmark: Double = 500.0
)

class HomeViewModel : ViewModel() {

    private val isUploadingAvatarState = MutableStateFlow(false)

    private val currentUserId: String
        get() = FirebaseManager.currentUserId ?: ""

    val uiState: StateFlow<HomeDashboardUiState> = combine(
        FirebaseManager.observeUserProfile(),
        FirebaseManager.observePersonalExpenses(),
        FirebaseManager.observeDirectSplits(),
        FirebaseManager.observeAllUserGroupExpenses(),
        isUploadingAvatarState
    ) { profile, personalExpenses, directSplits, groupExpenses, isUploadingAvatar ->
        calculateDashboardState(
            profile = profile,
            personalExpenses = personalExpenses,
            directSplits = directSplits,
            groupExpenses = groupExpenses,
            isUploadingAvatar = isUploadingAvatar,
            uid = currentUserId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeDashboardUiState()
    )

    fun confirmSettlement(splitId: String, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                FirebaseManager.confirmDirectSplitSettlement(splitId)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }

    fun declineSettlement(splitId: String, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                FirebaseManager.declineDirectSplitSettlement(splitId)
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri, onComplete: (Boolean, String?) -> Unit = { _, _ -> }) {
        isUploadingAvatarState.value = true
        viewModelScope.launch {
            try {
                val base64 = uriToBase64(context, uri)
                if (base64 != null) {
                    FirebaseManager.updateAvatarUrl(base64)
                    onComplete(true, null)
                } else {
                    onComplete(false, "Failed to process image")
                }
            } catch (e: Exception) {
                onComplete(false, e.message)
            } finally {
                isUploadingAvatarState.value = false
            }
        }
    }

    companion object {
        fun calculateDashboardState(
            profile: UserProfile?,
            personalExpenses: List<PersonalExpense>,
            directSplits: List<DirectSplit>,
            groupExpenses: List<GroupExpenseWithContext>,
            isUploadingAvatar: Boolean = false,
            uid: String,
            now: Long = System.currentTimeMillis()
        ): HomeDashboardUiState {
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = now }

            // Current month start
            val monthCal = (calendar.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val currentMonthStart = monthCal.timeInMillis

            val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
            val currentDayOfMonth = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            val daysRemaining = (daysInMonth - currentDayOfMonth + 1).coerceAtLeast(1)

            // Monthly Outflow
            val soloMonthly = personalExpenses.filter { it.date >= currentMonthStart }.sumOf { it.amount }
            val directMonthly = directSplits.filter { it.date >= currentMonthStart }.sumOf { it.myShare }
            val groupMonthly = groupExpenses.filter { it.expense.date >= currentMonthStart }.sumOf {
                it.expense.splits[uid] ?: 0.0
            }
            val totalPersonalSpent = soloMonthly + directMonthly + groupMonthly

            val budgetLimit = (profile?.budget?.limit ?: 15000.0).let { if (it <= 0) 15000.0 else it }
            val budgetProgress = if (budgetLimit > 0) (totalPersonalSpent / budgetLimit).coerceIn(0.0, 1.0).toFloat() else 0f
            val remainingBudget = budgetLimit - totalPersonalSpent
            val dailyAllowance = if (remainingBudget > 0) remainingBudget / daysRemaining else 0.0

            // Direct Receivable & Payable
            val directRec = directSplits.filter { it.status == "PENDING" && it.paidBy == uid }.sumOf { it.myShare }
            val groupRec = groupExpenses.filter { it.expense.paidBy == uid && it.expense.splitMode != "NONE" }.sumOf {
                it.expense.amount - (it.expense.splits[uid] ?: 0.0)
            }
            val totalDirectReceivable = directRec + groupRec

            val directPay = directSplits.filter { it.status == "PENDING" && it.paidBy != uid }.sumOf { it.myShare }
            val groupPay = groupExpenses.filter { it.expense.paidBy != uid && it.expense.splitMode != "NONE" }.sumOf {
                it.expense.splits[uid] ?: 0.0
            }
            val totalDirectPayable = directPay + groupPay

            val netPosition = totalDirectReceivable - totalDirectPayable

            // Pending settlement requiring user's approval
            val pendingSettlement = directSplits.firstOrNull { it.status == "WAITING_APPROVAL" && it.paidBy == uid }

            // 7-day spend array for current week (Monday to Sunday)
            val weekCal = (calendar.clone() as java.util.Calendar).apply {
                firstDayOfWeek = java.util.Calendar.MONDAY
                set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val mondayStart = weekCal.timeInMillis
            val dayMillis = 24L * 60 * 60 * 1000
            val spends = DoubleArray(7) { 0.0 }

            personalExpenses.forEach { exp ->
                val diff = exp.date - mondayStart
                if (diff >= 0 && diff < 7 * dayMillis) {
                    val idx = (diff / dayMillis).toInt().coerceIn(0, 6)
                    spends[idx] += exp.amount
                }
            }
            directSplits.forEach { split ->
                val diff = split.date - mondayStart
                if (diff >= 0 && diff < 7 * dayMillis) {
                    val idx = (diff / dayMillis).toInt().coerceIn(0, 6)
                    spends[idx] += split.myShare
                }
            }
            groupExpenses.forEach { ge ->
                val diff = ge.expense.date - mondayStart
                if (diff >= 0 && diff < 7 * dayMillis) {
                    val idx = (diff / dayMillis).toInt().coerceIn(0, 6)
                    spends[idx] += (ge.expense.splits[uid] ?: 0.0)
                }
            }

            val dow = calendar.get(java.util.Calendar.DAY_OF_WEEK)
            val currentDayIdx = if (dow == java.util.Calendar.SUNDAY) 6 else dow - 2

            // Category breakdown for current month
            val catMap = mutableMapOf<String, Double>()
            personalExpenses.filter { it.date >= currentMonthStart }.forEach { pe ->
                val cat = pe.category.ifBlank { "Other" }
                catMap[cat] = (catMap[cat] ?: 0.0) + pe.amount
            }
            directSplits.filter { it.date >= currentMonthStart }.forEach { ds ->
                val cat = ds.category.ifBlank { "Splits" }
                catMap[cat] = (catMap[cat] ?: 0.0) + ds.myShare
            }
            groupExpenses.filter { it.expense.date >= currentMonthStart }.forEach { ge ->
                val cat = ge.expense.category.ifBlank { "Group" }
                catMap[cat] = (catMap[cat] ?: 0.0) + (ge.expense.splits[uid] ?: 0.0)
            }
            val categoryBreakdown = catMap.toList().sortedByDescending { it.second }

            // Recent activity
            val splitsActivities = directSplits.map { split ->
                RecentActivityItem(
                    id = split.id,
                    title = if (split.description.isNotEmpty()) split.description else "Split",
                    subtitle = if (split.status == "SETTLED") "Settled" else if (split.status == "WAITING_APPROVAL") "Approval Pending" else if (split.paidBy == uid) "You paid" else "You owe",
                    amount = split.myShare,
                    date = split.date,
                    isPositive = split.paidBy == uid,
                    isSettled = split.status == "SETTLED",
                    rawSplit = split
                )
            }
            val personalActivities = personalExpenses.map { pe ->
                RecentActivityItem(
                    id = pe.id,
                    title = if (pe.description.isNotEmpty()) pe.description else pe.category,
                    subtitle = "Personal Expense • ${pe.category}",
                    amount = pe.amount,
                    date = pe.date,
                    isPositive = false,
                    isSettled = true,
                    rawSplit = null,
                    rawPersonal = pe
                )
            }
            val groupActivities = groupExpenses.map { ge ->
                val exp = ge.expense
                val isTracker = exp.splitMode == "NONE"
                val myShare = exp.splits[uid] ?: 0.0
                val isPayer = exp.paidBy == uid
                val netAmount = if (isTracker) exp.amount else if (isPayer) (exp.amount - myShare) else myShare
                val subtitle = if (isTracker) {
                    "${ge.groupName} • ${if (isPayer) "You paid" else "Group expense"}"
                } else {
                    "${ge.groupName} • ${if (isPayer) "You lent" else "You owe"}"
                }
                RecentActivityItem(
                    id = exp.id,
                    title = if (exp.description.isNotEmpty()) exp.description else exp.category,
                    subtitle = subtitle,
                    amount = netAmount,
                    date = exp.date,
                    isPositive = isPayer,
                    isSettled = isTracker,
                    rawSplit = null,
                    rawGroupExpense = ge
                )
            }

            val combinedActivities = (splitsActivities + personalActivities + groupActivities).sortedByDescending { it.date }
            val curatedActivities = combinedActivities.take(3)
            val weekTotalSpend = spends.sum()
            val maxSpend = (spends.maxOrNull() ?: 1.0).coerceAtLeast(100.0)
            val dailyBudgetBenchmark = if (budgetLimit > 0) budgetLimit / daysInMonth else 500.0

            return HomeDashboardUiState(
                isLoading = false,
                userProfile = profile,
                isUploadingAvatar = isUploadingAvatar,
                netPosition = netPosition,
                totalDirectReceivable = totalDirectReceivable,
                totalDirectPayable = totalDirectPayable,
                totalPersonalSpent = totalPersonalSpent,
                budgetLimit = budgetLimit,
                budgetProgress = budgetProgress,
                remainingBudget = remainingBudget,
                dailyAllowance = dailyAllowance,
                daysRemaining = daysRemaining,
                pendingSettlementApproval = pendingSettlement,
                currentWeekDaySpends = spends.toList(),
                currentDayOfWeekIdx = currentDayIdx,
                categoryBreakdown = categoryBreakdown,
                combinedRecentActivities = combinedActivities,
                curatedActivities = curatedActivities,
                weekTotalSpend = weekTotalSpend,
                maxSpend = maxSpend,
                dailyBudgetBenchmark = dailyBudgetBenchmark
            )
        }
    }
}
