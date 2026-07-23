package com.splitsmith.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.google.firebase.auth.FirebaseAuth
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.ui.auth.AuthScreen
import com.splitsmith.app.ui.expense.AddExpenseScreen
import com.splitsmith.app.ui.group.GroupDetailScreen
import com.splitsmith.app.ui.home.HomeScreen
import com.splitsmith.app.ui.onboarding.OnboardingScreen

@Composable
fun MainNavigation() {
    val initialKey = remember {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Auth
        } else {
            Home
        }
    }

    val backStack = rememberNavBackStack(initialKey)

    LaunchedEffect(FirebaseManager.sharedImageUri) {
        val uri = FirebaseManager.sharedImageUri
        if (uri != null) {
            FirebaseManager.sharedImageUri = null
            backStack.add(SlipImportKey(uri.toString()))
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = Modifier.fillMaxSize(),
        entryProvider = entryProvider {
            entry<Auth> {
                AuthScreen(
                    onAuthSuccess = { isNewUser ->
                        if (isNewUser) {
                            backStack.removeLastOrNull()
                            backStack.add(Onboarding)
                        } else {
                            backStack.removeLastOrNull()
                            backStack.add(Home)
                        }
                    }
                )
            }
            entry<Onboarding> {
                OnboardingScreen(
                    onOnboardingComplete = {
                        backStack.removeLastOrNull()
                        backStack.add(Home)
                    }
                )
            }
            entry<Home> {
                HomeScreen(
                    onNavigateToGroup = { groupId ->
                        backStack.add(GroupDetail(groupId))
                    },
                    onNavigateToQuickSplit = {
                        backStack.add(QuickSplit)
                    },
                    onNavigateToQRCode = {
                        backStack.add(QRCode)
                    },
                    onNavigateToReports = {
                        backStack.add(ReportsScreenKey())
                    },
                    onNavigateToAddExpense = { gid, eid ->
                        backStack.add(AddExpense(gid, eid))
                    },
                    onSignOut = {
                        backStack.removeLastOrNull()
                        backStack.add(Auth)
                    }
                )
            }
            entry<GroupDetail> { key ->
                GroupDetailScreen(
                    groupId = key.groupId,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToAddExpense = { gid, eid ->
                        backStack.add(AddExpense(gid, eid))
                    },
                    onNavigateToReports = { gId ->
                        backStack.add(ReportsScreenKey(initialGroupId = gId))
                    }
                )
            }
            entry<AddExpense> { key ->
                AddExpenseScreen(
                    groupId = key.groupId,
                    expenseId = key.expenseId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<QuickSplit> {
                com.splitsmith.app.ui.quicksplit.QuickSplitScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<QRCode> {
                com.splitsmith.app.ui.quicksplit.QRCodeScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<ReportsScreenKey> { key ->
                com.splitsmith.app.ui.reports.ReportsScreen(
                    initialGroupId = key.initialGroupId,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<SlipImportKey> { key ->
                com.splitsmith.app.ui.slip.SlipImportScreen(
                    imageUriStr = key.imageUriStr,
                    onBack = { backStack.removeLastOrNull() },
                    onNavigateToQuickSplit = {
                        backStack.add(QuickSplit)
                    },
                    onNavigateToAddExpense = { gid, eid ->
                        backStack.add(AddExpense(gid, eid))
                    }
                )
            }
        }
    )
}


