@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.trackly.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.R
import com.example.trackly.presentation.common.HabitTemplate
import com.example.trackly.presentation.common.HabitTemplates
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.formatValue
import com.example.trackly.presentation.common.rememberLocale
import com.example.trackly.presentation.common.unitLabel
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.components.ProgressRing
import com.example.trackly.ui.theme.TracklyTheme
import com.example.trackly.ui.theme.habitAccent
import com.example.trackly.ui.theme.habitContainer
import kotlinx.coroutines.launch

private const val INTRO_PAGES = 3

@Composable
fun OnboardingScreen(onFinished: (openCreate: Boolean) -> Unit) {
    val viewModel = appViewModel {
        OnboardingViewModel(it.settingsRepository, it.saveHabit, it.premiumPolicy, it.todayProvider)
    }
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    val pagerState = rememberPagerState(pageCount = { INTRO_PAGES + 1 })
    val scope = rememberCoroutineScope()

    val names = HabitTemplates.all.associate { it.key to stringResource(it.nameRes) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OnboardingEvent.Finished -> currentOnFinished(event.openCreate)
                is OnboardingEvent.LimitReached ->
                    snackbar.showSnackbar(context.getString(R.string.onboarding_limit, event.max))
                OnboardingEvent.Failed -> snackbar.showSnackbar(context.getString(R.string.error_action_failed))
            }
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize().testTag("onboarding")) {
        CenteredContent(maxWidth = 560.dp) {
            Column(modifier = Modifier.fillMaxSize().padding(top = top, bottom = bottom)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    if (page < INTRO_PAGES) {
                        IntroPage(page)
                    } else {
                        TemplatePage(selected = selected, onToggle = viewModel::toggle)
                    }
                }

                PageDots(count = INTRO_PAGES + 1, current = pagerState.currentPage)

                val onTemplates = pagerState.currentPage == INTRO_PAGES
                if (!onTemplates) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(INTRO_PAGES) } },
                            modifier = Modifier.testTag("onboarding_skip"),
                        ) { Text(stringResource(R.string.onboarding_skip)) }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            modifier = Modifier.height(52.dp).testTag("onboarding_next"),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(stringResource(R.string.onboarding_next), modifier = Modifier.padding(horizontal = 12.dp)) }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { viewModel.finish(names, openCreate = false) },
                            enabled = selected.isNotEmpty() && !saving,
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_start"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                if (selected.isEmpty()) {
                                    stringResource(R.string.onboarding_start)
                                } else {
                                    stringResource(R.string.onboarding_start_count, selected.size)
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.finish(names, openCreate = true) },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("onboarding_custom"),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.onboarding_custom))
                        }
                        TextButton(
                            onClick = viewModel::skip,
                            enabled = !saving,
                            modifier = Modifier.align(Alignment.CenterHorizontally).testTag("onboarding_later"),
                        ) { Text(stringResource(R.string.onboarding_later)) }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 96.dp, start = 16.dp, end = 16.dp),
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 24.dp else 8.dp, label = "dotWidth")
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(8.dp)
                    .width(width)
                    .background(color, CircleShape),
            )
        }
    }
}

// region intro pages

@Composable
private fun IntroPage(page: Int) {
    val title = when (page) {
        0 -> R.string.onboarding_title_1
        1 -> R.string.onboarding_title_2
        else -> R.string.onboarding_title_3
    }
    val body = when (page) {
        0 -> R.string.onboarding_body_1
        1 -> R.string.onboarding_body_2
        else -> R.string.onboarding_body_3
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.height(260.dp), contentAlignment = Alignment.Center) {
            when (page) {
                0 -> RingIllustration()
                1 -> CardsIllustration()
                else -> StreakIllustration()
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RingIllustration() {
    val primary = MaterialTheme.colorScheme.primary
    ProgressRing(
        progress = 0.78f,
        color = primary,
        trackColor = MaterialTheme.colorScheme.primaryContainer,
        strokeWidth = 22.dp,
        modifier = Modifier.size(220.dp),
    ) {
        Box(
            modifier = Modifier.size(104.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = primary, modifier = Modifier.size(64.dp))
        }
    }
}

@Composable
private fun CardsIllustration() {
    val isDark = TracklyTheme.extras.isDark
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val samples = listOf(
        Triple("💧", 0xFF3B9BE8.toInt(), 0.7f),
        Triple("🧘", 0xFFC166D6.toInt(), 0.45f),
        Triple("📚", 0xFFE9B824.toInt(), 1f),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        samples.forEach { (emoji, argb, progress) ->
            val color = Color(argb)
            Surface(shape = RoundedCornerShape(22.dp), color = color.habitContainer(surface, isDark), shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).background(color.habitAccent(isDark).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 22.sp) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(8.dp)
                                .background(color.habitAccent(isDark), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakIllustration() {
    val extras = TracklyTheme.extras
    val primary = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = extras.streak,
                modifier = Modifier.size(56.dp),
            )
            Text("21", style = MaterialTheme.typography.displayLarge, color = extras.streak)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(110.dp),
        ) {
            listOf(0.5f, 0.7f, 0.6f, 0.85f, 0.75f, 1f, 0.9f).forEach { fraction ->
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .fillMaxHeight(fraction)
                        .background(primary.copy(alpha = 0.35f + 0.65f * fraction), RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

// endregion

// region templates

@Composable
private fun TemplatePage(selected: Set<String>, onToggle: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_templates_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.onboarding_templates_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().testTag("template_grid"),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(HabitTemplates.all, key = { it.key }) { template ->
                TemplateCard(template, template.key in selected) { onToggle(template.key) }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: HabitTemplate, selected: Boolean, onToggle: () -> Unit) {
    val isDark = TracklyTheme.extras.isDark
    val base = Color(template.colorArgb)
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest
    val container by animateColorAsState(
        base.habitContainer(surface, isDark, if (selected) 1f else 0f),
        label = "templateBackground",
    )
    val locale = rememberLocale()
    val name = stringResource(template.nameRes)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, base.habitAccent(isDark), RoundedCornerShape(22.dp)) else Modifier)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .testTag("template_${template.key}"),
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(template.icon, fontSize = 30.sp)
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val goalText = if (template.type == com.example.trackly.domain.model.HabitType.BOOLEAN) {
                    stringResource(R.string.template_daily)
                } else {
                    "${formatValue(template.goal, locale)} ${unitLabel(template.unit, null)}"
                }
                Text(goalText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(24.dp)
                        .background(base.habitAccent(isDark), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// endregion
