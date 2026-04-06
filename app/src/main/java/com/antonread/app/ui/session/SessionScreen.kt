package com.antonread.app.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antonread.app.data.model.Mode
import com.antonread.app.ui.theme.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars

private val modeLabels = mapOf(
    Mode.LETTERS    to "Буквы",
    Mode.EASY_WORDS to "Слова",
    Mode.HARD_WORDS to "Слова★"
)

@Composable
fun SessionScreen(
    viewModel: SessionViewModel,
    onNavigateToStats: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val systemBars = WindowInsets.systemBars.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        TopBar(
            topPadding = systemBars.calculateTopPadding(),
            currentMode = state.mode,
            unlockedCounts = state.unlockedCounts,
            onModeSelected = viewModel::setMode,
            onStats = onNavigateToStats,
            onDebugNewSession = viewModel::forceNewSession
        )

        // ── Content area (80%) ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f),
            contentAlignment = Alignment.Center
        ) {
            val phase = state.syllablePhase
            when {
                state.isLoading -> CircularProgressIndicator(color = PrimaryColor)
                phase != null -> ItemDisplay(text = phase.currentSyllable)
                state.currentItem == null -> Text(
                    text = "Готово!",
                    fontSize = 48.sp,
                    color = PrimaryColor,
                    textAlign = TextAlign.Center
                )
                else -> ItemDisplay(text = state.currentItem!!.content)
            }
        }

        // ── Answer buttons (20%) ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f)
                .padding(
                    start = 16.dp + systemBars.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    end = 16.dp + systemBars.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                    top = 8.dp,
                    bottom = 8.dp + systemBars.calculateBottomPadding()
                ),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val enabled = state.syllablePhase != null || state.currentItem != null
            AnswerButton(
                label = "✓  Верно",
                color = CorrectColor,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = viewModel::answerCorrect
            )
            AnswerButton(
                label = "✗  Нет",
                color = WrongColor,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = viewModel::answerWrong
            )
        }
    }
}

@Composable
private fun TopBar(
    topPadding: androidx.compose.ui.unit.Dp,
    currentMode: Mode,
    unlockedCounts: Map<Mode, Int>,
    onModeSelected: (Mode) -> Unit,
    onStats: () -> Unit,
    onDebugNewSession: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp + topPadding, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Mode.entries.forEach { mode ->
                val count = unlockedCounts[mode] ?: 0
                val selected = mode == currentMode
                FilterChip(
                    selected = selected,
                    onClick = { onModeSelected(mode) },
                    label = {
                        Text(
                            text = "${modeLabels[mode]} $count",
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryColor,
                        selectedLabelColor = Color.White,
                        containerColor = BackgroundColor,
                        labelColor = PrimaryColor
                    )
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onStats) {
            Text("Статистика", color = PrimaryColor, fontSize = 13.sp)
        }
        TextButton(onClick = onDebugNewSession) {
            Text("DBG", color = DimColor, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ItemDisplay(text: String) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        val letterCount = text.count { it != '·' }
        val dotCount    = text.count { it == '·' }
        val effectiveCount = (letterCount + dotCount * 0.5f).coerceAtLeast(1f)
        val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        val maxWidthDp = maxWidth.value / fontScale   // sp ≈ dp when fontScale=1
        val computed = (maxWidthDp * 0.80f / (effectiveCount * 0.58f)).coerceIn(48f, 220f)
        val annotated = buildAnnotatedString {
            for (ch in text) {
                if (ch == '·') {
                    withStyle(SpanStyle(color = DimColor)) { append(ch) }
                } else {
                    withStyle(SpanStyle(color = PrimaryColor)) { append(ch) }
                }
            }
        }
        Text(
            text = annotated,
            fontSize = computed.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = (computed * 1.05f).sp
        )
    }
}

@Composable
private fun AnswerButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = DimColor
        )
    ) {
        Text(
            text = label,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
