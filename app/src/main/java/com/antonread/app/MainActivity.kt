package com.antonread.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antonread.app.data.db.AppDatabase
import com.antonread.app.data.repository.LearningRepository
import com.antonread.app.ui.session.SessionScreen
import com.antonread.app.ui.session.SessionViewModel
import com.antonread.app.ui.stats.StatsScreen
import com.antonread.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.get(applicationContext)
        val repo = LearningRepository(db)

        setContent {
            AppTheme {
                AppNavigation(repo)
            }
        }
    }
}

@Composable
private fun AppNavigation(repo: LearningRepository) {
    var showStats by remember { mutableStateOf(false) }
    val sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory(repo))

    if (showStats) {
        StatsScreen(repo = repo, onBack = { showStats = false })
    } else {
        SessionScreen(
            viewModel = sessionViewModel,
            onNavigateToStats = { showStats = true }
        )
    }
}
