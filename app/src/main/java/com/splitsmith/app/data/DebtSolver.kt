package com.splitsmith.app.data

object DebtSolver {

    /**
     * Simplifies debts in a group.
     * Takes a map of member UID to their net balance (what they paid - their total share).
     * Returns a list of simplified [Debt] transactions (who owes who how much).
     */
    fun resolveDebts(memberNetBalances: Map<String, Double>): List<Debt> {
        val debts = mutableListOf<Debt>()

        // Debtors have net balance < 0 (they owe money)
        val debtors = memberNetBalances.filter { it.value < -0.01 }
            .map { it.key to -it.value }
            .sortedByDescending { it.second }
            .toMutableList()

        // Creditors have net balance > 0 (they are owed money)
        val creditors = memberNetBalances.filter { it.value > 0.01 }
            .map { it.key to it.value }
            .sortedByDescending { it.second }
            .toMutableList()

        var dIdx = 0
        var cIdx = 0

        while (dIdx < debtors.size && cIdx < creditors.size) {
            val debtor = debtors[dIdx]
            val creditor = creditors[cIdx]

            val settleAmount = minOf(debtor.second, creditor.second)
            if (settleAmount > 0.01) {
                debts.add(
                    Debt(
                        fromUser = debtor.first,
                        toUser = creditor.first,
                        amount = Math.round(settleAmount * 100.0) / 100.0 // Round to 2 decimal places
                    )
                )
            }

            debtors[dIdx] = debtor.first to (debtor.second - settleAmount)
            creditors[cIdx] = creditor.first to (creditor.second - settleAmount)

            if (debtors[dIdx].second <= 0.01) {
                dIdx++
            }
            if (creditors[cIdx].second <= 0.01) {
                cIdx++
            }
        }
        return debts
    }

    /**
     * Calculates the net balance of each user in a group based on expenses and settlements.
     */
    fun calculateNetBalances(
        members: List<String>,
        expenses: List<Expense>,
        settlements: List<Settlement>
    ): Map<String, Double> {
        val net = members.associateWith { 0.0 }.toMutableMap()

        // Apply expenses
        for (expense in expenses) {
            // Payer gets credited the full amount
            net[expense.paidBy] = (net[expense.paidBy] ?: 0.0) + expense.amount

            // Each member in splits gets debited their share
            for ((uid, share) in expense.splits) {
                net[uid] = (net[uid] ?: 0.0) - share
            }
        }

        // Apply CONFIRMED settlements
        for (settlement in settlements) {
            if (settlement.status == "CONFIRMED") {
                // Sender gets credited (they paid off their debt)
                net[settlement.fromUser] = (net[settlement.fromUser] ?: 0.0) + settlement.amount
                // Receiver gets debited (they received their money)
                net[settlement.toUser] = (net[settlement.toUser] ?: 0.0) - settlement.amount
            }
        }

        return net
    }
}
