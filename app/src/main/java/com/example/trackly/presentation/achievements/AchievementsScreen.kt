@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.domain.logic.Achievement
import com.example.trackly.domain.logic.AchievementKind
import com.example.trackly.domain.logic.AchievementProgress
import com.example.trackly.presentation.common.UiState
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.ErrorState
import com.example.trackly.ui.components.LoadingState

@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { AchievementsViewModel(it.habitRepository, it.todayProvider) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.achievements_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(onRetry = viewModel::retry)
                is UiState.Content -> CenteredContent { AchievementsBody(current.data) }
            }
        }
    }
}

@Composable
private fun AchievementsBody(content: AchievementsContent) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize().testTag("achievements_grid"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary", span = { GridItemSpan(maxLineSpan) }) { SummaryCard(content) }
        items(content.items, key = { it.achievement.id }) { AchievementCard(it) }
    }
}

@Composable
private fun SummaryCard(content: AchievementsContent) {
    val total = content.items.size
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("🏆", fontSize = 40.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.achievements_unlocked, content.unlockedCount, total),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("achievements_count"),
                    )
                    val next = content.next
                    Text(
                        if (next == null) {
                            stringResource(R.string.achievements_all_done)
                        } else {
                            stringResource(R.string.achievements_next, achievementTitle(next.achievement))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            ProgressBar(
                fraction = if (total == 0) 0f else content.unlockedCount.toFloat() / total,
                color = MaterialTheme.colorScheme.primary,
                track = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            )
        }
    }
}

@Composable
private fun AchievementCard(item: AchievementProgress) {
    val unlocked = item.unlocked
    val achievement = item.achievement
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (unlocked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().testTag("achievement_${achievement.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(achievement.emoji, fontSize = 34.sp, modifier = Modifier.alpha(if (unlocked) 1f else 0.35f))
            Text(
                achievementTitle(achievement),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Text(
                achievementDescription(achievement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 3,
            )
            if (unlocked) {
                Text(
                    "✓ " + stringResource(R.string.achievement_unlocked),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                ProgressBar(
                    fraction = item.fraction,
                    color = MaterialTheme.colorScheme.primary,
                    track = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    "${minOf(item.current, achievement.target)} / ${achievement.target}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color, track: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

@Composable
fun achievementTitle(achievement: Achievement): String {
    val n = achievement.target
    return when (achievement.kind) {
        AchievementKind.FIRST_STEP -> stringResource(R.string.achv_first_title)
        AchievementKind.STREAK -> pluralStringResource(R.plurals.achv_streak_title, n, n)
        AchievementKind.COMPLETIONS -> pluralStringResource(R.plurals.achv_done_title, n, n)
        AchievementKind.PERFECT_DAYS -> pluralStringResource(R.plurals.achv_perfect_title, n, n)
        AchievementKind.HABITS -> pluralStringResource(R.plurals.achv_habits_title, n, n)
        AchievementKind.ACTIVE_DAYS -> pluralStringResource(R.plurals.achv_days_title, n, n)
    }
}

@Composable
private fun achievementDescription(achievement: Achievement): String = stringResource(
    when (achievement.kind) {
        AchievementKind.FIRST_STEP -> R.string.achv_first_desc
        AchievementKind.STREAK -> R.string.achv_streak_desc
        AchievementKind.COMPLETIONS -> R.string.achv_done_desc
        AchievementKind.PERFECT_DAYS -> R.string.achv_perfect_desc
        AchievementKind.HABITS -> R.string.achv_habits_desc
        AchievementKind.ACTIVE_DAYS -> R.string.achv_days_desc
    },
)
