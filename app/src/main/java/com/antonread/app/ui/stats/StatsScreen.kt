package com.antonread.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonread.app.data.db.ItemEntity
import com.antonread.app.data.model.ItemState
import com.antonread.app.data.repository.LearningRepository
import com.antonread.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(repo: LearningRepository, onBack: () -> Unit) {
    var stats by remember { mutableStateOf<LearningRepository.Stats?>(null) }

    LaunchedEffect(Unit) { stats = repo.getStats() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Назад", color = PrimaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor)
            )
        },
        containerColor = BackgroundColor
    ) { padding ->
        val s = stats
        if (s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryColor)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // ── Letters ──────────────────────────────────────────────────
            val knownLetters = s.letters.filter { it.isKnown() }
            val unknownLetters = s.letters.filter { !it.isKnown() }
            StatSection(
                title = "Буквы",
                knownCount = knownLetters.size,
                totalCount = s.letters.size
            ) {
                ItemChips(known = knownLetters, unknown = unknownLetters)
            }

            // ── Sessions ──────────────────────────────────────────────────
            Text(
                text = "Занятий: ${s.sessionCount}",
                fontSize = 16.sp,
                color = PrimaryColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StatSection(
    title: String,
    knownCount: Int,
    totalCount: Int,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryColor)
            Spacer(Modifier.width(12.dp))
            Text("$knownCount / $totalCount знает", fontSize = 15.sp, color = PrimaryColor.copy(alpha = 0.6f))
        }
        LinearProgressIndicator(
            progress = { if (totalCount > 0) knownCount.toFloat() / totalCount else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = CorrectColor,
            trackColor = SurfaceColor
        )
        content()
    }
}

@Composable
private fun ItemChips(known: List<ItemEntity>, unknown: List<ItemEntity>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        known.forEach { item ->
            ItemChip(text = item.content, dimmed = false)
        }
        unknown.forEach { item ->
            ItemChip(text = item.content, dimmed = true)
        }
    }
}

@Composable
private fun ItemChip(text: String, dimmed: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (dimmed) SurfaceColor else PrimaryColor.copy(alpha = 0.12f),
        modifier = Modifier.padding(0.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 18.sp,
            fontWeight = if (dimmed) FontWeight.Normal else FontWeight.Bold,
            color = if (dimmed) DimColor else KnownColor
        )
    }
}

private fun ItemEntity.isKnown() = state == ItemState.KNOWN || state == ItemState.FLAGGED
