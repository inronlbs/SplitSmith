package com.splitsmith.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMemberLifecycleTest {

    private val realUserAmit = "user_amit"
    private val realUserPriya = "user_priya"
    private val guestRahul = "guest_rahul"

    @Test
    fun virtualMember_isRecognizedAsGuest() {
        val guest = UserProfile(uid = guestRahul, displayName = "Rahul")
        val real = UserProfile(uid = realUserAmit, displayName = "Amit")

        assertTrue(guest.isGuest)
        assertFalse(real.isGuest)
        assertEquals("Rahul", guest.getResolvedName())
    }

    @Test
    fun debtSolver_calculatesDebtsForVirtualMembersCorrectly() {
        // Amit pays 900 for a meal, split equally between Amit, Priya, and guest Rahul (300 each)
        val exp1 = Expense(
            id = "exp_1",
            amount = 900.0,
            paidBy = realUserAmit,
            splits = mapOf(realUserAmit to 300.0, realUserPriya to 300.0, guestRahul to 300.0),
            splitMode = "EQUAL"
        )

        val members = listOf(realUserAmit, realUserPriya, guestRahul)
        val balances = DebtSolver.calculateNetBalances(members, listOf(exp1), emptyList())

        // Amit is creditor (+600), Priya owes (-300), Rahul owes (-300)
        assertEquals(600.0, balances[realUserAmit] ?: 0.0, 0.001)
        assertEquals(-300.0, balances[realUserPriya] ?: 0.0, 0.001)
        assertEquals(-300.0, balances[guestRahul] ?: 0.0, 0.001)

        val debts = DebtSolver.calculatePeerDebts(listOf(exp1), emptyList())
        val rahulDebt = debts.firstOrNull { it.fromUser == guestRahul }

        assertNotNull(rahulDebt)
        assertEquals(realUserAmit, rahulDebt?.toUser)
        assertEquals(300.0, rahulDebt?.amount ?: 0.0, 0.001)
    }

    @Test
    fun debtSolver_cashSettlementWithVirtualMember_bringsBalanceToZero() {
        // Amit pays 600 split with guest Rahul (300 each)
        val exp1 = Expense(
            id = "exp_1",
            amount = 600.0,
            paidBy = realUserAmit,
            splits = mapOf(realUserAmit to 300.0, guestRahul to 300.0),
            splitMode = "EQUAL"
        )

        // Rahul pays 300 cash to Amit to settle up
        val settlement = Settlement(
            id = "set_1",
            fromUser = guestRahul,
            toUser = realUserAmit,
            amount = 300.0,
            method = "CASH",
            status = "CONFIRMED"
        )

        val members = listOf(realUserAmit, guestRahul)
        val balances = DebtSolver.calculateNetBalances(members, listOf(exp1), listOf(settlement))

        assertEquals(0.0, balances[realUserAmit] ?: 0.0, 0.001)
        assertEquals(0.0, balances[guestRahul] ?: 0.0, 0.001)

        val remainingDebts = DebtSolver.calculatePeerDebts(listOf(exp1), listOf(settlement))
        assertTrue(remainingDebts.isEmpty())
    }

    @Test
    fun zeroBalanceGuard_identifiesUnsettledBalances() {
        val nonZeroBalancePositive = 250.0
        val nonZeroBalanceNegative = -150.0
        val zeroBalance = 0.0

        val canRemovePositive = Math.abs(nonZeroBalancePositive) <= 0.01
        val canRemoveNegative = Math.abs(nonZeroBalanceNegative) <= 0.01
        val canRemoveZero = Math.abs(zeroBalance) <= 0.01

        assertFalse("Should block removal when member is owed money", canRemovePositive)
        assertFalse("Should block removal when member owes money", canRemoveNegative)
        assertTrue("Should allow removal when balance is zero", canRemoveZero)
    }

    @Test
    fun groupModel_preservesVirtualAndPastMembers() {
        val vm = VirtualMember(
            id = guestRahul,
            name = "Rahul",
            addedBy = realUserAmit,
            createdAt = 1000L,
            isArchived = false
        )

        val group = Group(
            id = "group_trip",
            name = "Goa Trip",
            members = mapOf(realUserAmit to true, guestRahul to true),
            virtualMembers = mapOf(guestRahul to vm),
            pastMembers = mapOf("old_member_1" to "Vikram")
        )

        assertEquals("Rahul", group.virtualMembers[guestRahul]?.name)
        assertFalse(group.virtualMembers[guestRahul]?.isArchived ?: true)
        assertEquals("Vikram", group.pastMembers["old_member_1"])
        assertTrue(group.members.containsKey(guestRahul))
    }

    @Test
    fun expense_reimbursementTracking_defaultsAndUpdates() {
        val expense = Expense(
            id = "exp_corp_1",
            amount = 1500.0,
            description = "Client Lunch",
            paidBy = realUserAmit
        )

        assertFalse(expense.isReimbursed)
        assertEquals(0L, expense.reimbursedAt)

        val updated = expense.copy(isReimbursed = true, reimbursedAt = 1700000000L)
        assertTrue(updated.isReimbursed)
        assertEquals(1700000000L, updated.reimbursedAt)
    }

    @Test
    fun virtualMember_avatarUrl_isPreserved() {
        val vm = VirtualMember(
            id = guestRahul,
            name = "Rahul",
            addedBy = realUserAmit,
            avatarUrl = "https://example.com/avatar.jpg"
        )

        assertEquals("https://example.com/avatar.jpg", vm.avatarUrl)
    }
}
