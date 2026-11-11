package com.splitsmith.app.ui.home

import com.splitsmith.app.data.BudgetConfig
import com.splitsmith.app.data.DirectSplit
import com.splitsmith.app.data.Expense
import com.splitsmith.app.data.GroupExpenseWithContext
import com.splitsmith.app.data.PersonalExpense
import com.splitsmith.app.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HomeViewModelTest {

    private val testUid = "user_123"

    @Test
    fun calculateDashboardState_emptyInputs_returnsZeroBalances() {
        val state = HomeViewModel.calculateDashboardState(
            profile = null,
            personalExpenses = emptyList(),
            directSplits = emptyList(),
            groupExpenses = emptyList(),
            uid = testUid
        )

        assertEquals(0.0, state.netPosition, 0.001)
        assertEquals(0.0, state.totalDirectReceivable, 0.001)
        assertEquals(0.0, state.totalDirectPayable, 0.001)
        assertEquals(0.0, state.totalPersonalSpent, 0.001)
        assertEquals(15000.0, state.budgetLimit, 0.001)
        assertEquals(15000.0, state.remainingBudget, 0.001)
        assertTrue(state.curatedActivities.isEmpty())
        assertNull(state.pendingSettlementApproval)
    }

    @Test
    fun calculateDashboardState_netPosition_calculatesReceivableMinusPayableCorrectly() {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        // Direct split where testUid paid 500 (other person owes testUid 250)
        val splitLent = DirectSplit(
            id = "split_1",
            amount = 500.0,
            paidBy = testUid,
            withUser = "user_456",
            myShare = 250.0,
            status = "PENDING",
            date = now
        )

        // Direct split where user_789 paid 300 (testUid owes 150)
        val splitOwed = DirectSplit(
            id = "split_2",
            amount = 300.0,
            paidBy = "user_789",
            withUser = testUid,
            myShare = 150.0,
            status = "PENDING",
            date = now
        )

        // Group expense where testUid paid 1000 with 400 personal share (others owe 600)
        val groupExpLent = GroupExpenseWithContext(
            groupId = "group_1",
            groupName = "Goa Trip",
            expense = Expense(
                id = "exp_1",
                amount = 1000.0,
                paidBy = testUid,
                splits = mapOf(testUid to 400.0, "user_456" to 600.0),
                splitMode = "EXACT",
                date = now
            )
        )

        val state = HomeViewModel.calculateDashboardState(
            profile = null,
            personalExpenses = emptyList(),
            directSplits = listOf(splitLent, splitOwed),
            groupExpenses = listOf(groupExpLent),
            uid = testUid,
            now = now
        )

        // Receivable: 250 (direct) + 600 (group) = 850
        assertEquals(850.0, state.totalDirectReceivable, 0.001)
        // Payable: 150 (direct) + 0 (group) = 150
        assertEquals(150.0, state.totalDirectPayable, 0.001)
        // Net position: 850 - 150 = 700
        assertEquals(700.0, state.netPosition, 0.001)
    }

    @Test
    fun calculateDashboardState_budgetAndAllowance_calculatesDaysRemainingAndPacing() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 10)
        }
        val now = cal.timeInMillis
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val expectedDaysRemaining = daysInMonth - 10 + 1

        val personalExp = PersonalExpense(
            id = "pe_1",
            amount = 3000.0,
            category = "Groceries",
            date = now
        )

        val state = HomeViewModel.calculateDashboardState(
            profile = UserProfile(budget = BudgetConfig(limit = 15000.0)),
            personalExpenses = listOf(personalExp),
            directSplits = emptyList(),
            groupExpenses = emptyList(),
            uid = testUid,
            now = now
        )

        assertEquals(3000.0, state.totalPersonalSpent, 0.001)
        assertEquals(12000.0, state.remainingBudget, 0.001)
        assertEquals(expectedDaysRemaining, state.daysRemaining)
        val expectedAllowance = 12000.0 / expectedDaysRemaining
        assertEquals(expectedAllowance, state.dailyAllowance, 0.01)
    }

    @Test
    fun calculateDashboardState_pendingSettlement_identifiesWaitingApprovalForCurrentUser() {
        val now = System.currentTimeMillis()
        val pendingSplit = DirectSplit(
            id = "split_waiting",
            amount = 400.0,
            paidBy = testUid,
            withUser = "user_peer",
            myShare = 200.0,
            status = "WAITING_APPROVAL",
            date = now
        )

        val state = HomeViewModel.calculateDashboardState(
            profile = null,
            personalExpenses = emptyList(),
            directSplits = listOf(pendingSplit),
            groupExpenses = emptyList(),
            uid = testUid,
            now = now
        )

        assertNotNull(state.pendingSettlementApproval)
        assertEquals("split_waiting", state.pendingSettlementApproval?.id)
    }

    @Test
    fun calculateDashboardState_curatedActivities_takesTopThreeChronologically() {
        val now = System.currentTimeMillis()
        val e1 = PersonalExpense(id = "1", amount = 100.0, date = now - 3000)
        val e2 = PersonalExpense(id = "2", amount = 200.0, date = now - 1000)
        val e3 = PersonalExpense(id = "3", amount = 300.0, date = now)
        val e4 = PersonalExpense(id = "4", amount = 400.0, date = now - 5000)

        val state = HomeViewModel.calculateDashboardState(
            profile = null,
            personalExpenses = listOf(e1, e2, e3, e4),
            directSplits = emptyList(),
            groupExpenses = emptyList(),
            uid = testUid,
            now = now
        )

        assertEquals(3, state.curatedActivities.size)
        assertEquals("3", state.curatedActivities[0].id)
        assertEquals("2", state.curatedActivities[1].id)
        assertEquals("1", state.curatedActivities[2].id)
    }

    @Test
    fun calculateDashboardState_excludesGroupExpensesNotInvolvingCurrentUser() {
        val now = System.currentTimeMillis()

        // 1. Expense between other members only (testUid not involved)
        val unrelatedGroupExp = GroupExpenseWithContext(
            groupId = "group_1",
            groupName = "Trip",
            expense = Expense(
                id = "exp_unrelated",
                amount = 1000.0,
                paidBy = "other_user_1",
                splits = mapOf("other_user_1" to 500.0, "other_user_2" to 500.0),
                splitMode = "EXACT",
                date = now
            )
        )

        // 2. Tracker expense logged by another member
        val otherMemberTrackerExp = GroupExpenseWithContext(
            groupId = "group_1",
            groupName = "Trip",
            expense = Expense(
                id = "exp_other_tracker",
                amount = 800.0,
                paidBy = "other_user_1",
                splits = emptyMap(),
                splitMode = "NONE",
                date = now - 1000
            )
        )

        // 3. Split expense where testUid owes
        val myOwedExp = GroupExpenseWithContext(
            groupId = "group_1",
            groupName = "Trip",
            expense = Expense(
                id = "exp_my_owed",
                amount = 600.0,
                paidBy = "other_user_1",
                splits = mapOf("other_user_1" to 300.0, testUid to 300.0),
                splitMode = "EXACT",
                date = now - 2000
            )
        )

        // 4. Split expense where testUid is the payer
        val myLentExp = GroupExpenseWithContext(
            groupId = "group_1",
            groupName = "Trip",
            expense = Expense(
                id = "exp_my_lent",
                amount = 900.0,
                paidBy = testUid,
                splits = mapOf(testUid to 300.0, "other_user_1" to 600.0),
                splitMode = "EXACT",
                date = now - 3000
            )
        )

        val state = HomeViewModel.calculateDashboardState(
            profile = null,
            personalExpenses = emptyList(),
            directSplits = emptyList(),
            groupExpenses = listOf(unrelatedGroupExp, otherMemberTrackerExp, myOwedExp, myLentExp),
            uid = testUid,
            now = now
        )

        val curatedIds = state.curatedActivities.map { it.id }
        // Both unrelated expenses must NOT be present
        assertTrue(!curatedIds.contains("exp_unrelated"))
        assertTrue(!curatedIds.contains("exp_other_tracker"))

        // Expenses involving testUid must be present
        assertTrue(curatedIds.contains("exp_my_owed"))
        assertTrue(curatedIds.contains("exp_my_lent"))
        assertEquals(2, state.curatedActivities.size)
    }
}
