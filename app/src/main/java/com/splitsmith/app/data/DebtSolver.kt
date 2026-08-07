package com.splitsmith.app.data

object DebtSolver {

    /**
     * Calculates peer-to-peer debts (who owes who) without global simplification.
     * This ensures individual balances are accurate and not redistributed.
     */
    fun calculatePeerDebts(
        expenses: List<Expense>,
        settlements: List<Settlement>
    ): List<Debt> {
        // Map of Pair(user1, user2) where user1 < user2, to the net amount user2 owes user1.
        // Positive balance = user2 owes user1. Negative balance = user1 owes user2.
        val balances = mutableMapOf<Pair<String, String>, Double>()

        for (expense in expenses) {
            val payer = expense.paidBy
            for ((borrower, share) in expense.splits) {
                if (payer != borrower && share > 0.0) {
                    val pair = if (payer < borrower) payer to borrower else borrower to payer
                    val current = balances[pair] ?: 0.0
                    // If payer is user1, user2 owes user1 -> positive
                    if (payer == pair.first) {
                        balances[pair] = current + share
                    } else {
                        balances[pair] = current - share
                    }
                }
            }
        }

        for (settlement in settlements) {
            if (settlement.status == "CONFIRMED") {
                val sender = settlement.fromUser
                val receiver = settlement.toUser
                if (sender != receiver) {
                    val pair = if (sender < receiver) sender to receiver else receiver to sender
                    val current = balances[pair] ?: 0.0
                    // If sender is user1, user1 gave money to user2, which means user2 owes user1 more (or user1 owes less) -> positive
                    if (sender == pair.first) {
                        balances[pair] = current + settlement.amount
                    } else {
                        balances[pair] = current - settlement.amount
                    }
                }
            }
        }

        val debts = mutableListOf<Debt>()
        for ((pair, balance) in balances) {
            val amt = Math.round(Math.abs(balance) * 100.0) / 100.0
            if (amt > 0.01) {
                if (balance > 0) {
                    debts.add(Debt(fromUser = pair.second, toUser = pair.first, amount = amt))
                } else {
                    debts.add(Debt(fromUser = pair.first, toUser = pair.second, amount = amt))
                }
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
