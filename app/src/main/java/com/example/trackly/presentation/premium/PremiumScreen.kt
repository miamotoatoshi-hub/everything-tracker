@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.premium

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackly.BuildConfig
import com.example.trackly.R
import com.example.trackly.billing.BillingConfig
import com.example.trackly.billing.BillingPeriod
import com.example.trackly.billing.PeriodUnit
import com.example.trackly.billing.PlanKind
import com.example.trackly.billing.PlanPricing
import com.example.trackly.billing.PremiumPlan
import com.example.trackly.billing.ProductState
import com.example.trackly.domain.model.EntitlementStatus
import com.example.trackly.domain.model.PremiumFeature
import com.example.trackly.presentation.common.appViewModel
import com.example.trackly.presentation.common.billingMessage
import com.example.trackly.presentation.common.findActivity
import com.example.trackly.presentation.common.messageRes
import com.example.trackly.presentation.common.openUrl
import com.example.trackly.presentation.settings.LegalKind
import com.example.trackly.ui.components.CenteredContent
import com.example.trackly.ui.theme.TracklyTheme

@Composable
fun PremiumScreen(
    reason: PremiumFeature?,
    onClose: () -> Unit,
    onOpenLegal: (LegalKind) -> Unit,
) {
    val viewModel = appViewModel { PremiumViewModel(it.billingRepository) }
    val productState by viewModel.productState.collectAsStateWithLifecycle()
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val isPremium by viewModel.isPremium.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = billingMessage(context, event)
            if (message != null) {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize().testTag("premium_screen")) {
        CenteredContent(maxWidth = 560.dp) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = top + 8.dp, bottom = bottom + 24.dp, start = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("premium_close")) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }
                Hero(isPremium = isPremium)
                if (reason != null && !isPremium) ReasonBanner(reason)
                Features()

                when {
                    isPremium -> ActiveCard(
                        canceled = entitlement.status == EntitlementStatus.ACTIVE_CANCELED,
                        lifetime = entitlement.isLifetime,
                        onManage = { openUrl(context, BillingConfig.manageSubscriptionUrl(context.packageName)) },
                    )
                    else -> {
                        if (entitlement.status == EntitlementStatus.PENDING) PendingBanner()
                        PlansCard(
                            state = productState,
                            canPurchase = entitlement.status != EntitlementStatus.PENDING,
                            onPurchase = { plan -> context.findActivity()?.let { viewModel.purchase(it, plan) } },
                            onRetry = viewModel::retry,
                        )
                    }
                }

                TextButton(
                    onClick = viewModel::restore,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("restore_purchases_button"),
                ) { Text(stringResource(R.string.settings_restore_purchases)) }

                Disclosure(hasLifetime = (productState as? ProductState.Available)?.plans?.any { it.kind == PlanKind.LIFETIME } == true)
                LegalLinks(onOpenLegal)
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 12.dp, start = 16.dp, end = 16.dp),
        )
    }
}

@Composable
private fun Hero(isPremium: Boolean) {
    val extras = TracklyTheme.extras
    val brush = Brush.linearGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiaryContainer),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(88.dp).background(brush, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
            )
        }
        Text(
            text = stringResource(if (isPremium) R.string.premium_title_active else R.string.premium_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.premium_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // Subtle accent so the paywall feels premium without extra assets.
        Box(modifier = Modifier.height(3.dp).size(width = 48.dp, height = 3.dp).background(extras.premium, RoundedCornerShape(50)))
    }
}

@Composable
private fun ReasonBanner(feature: PremiumFeature) {
    Surface(shape = RoundedCornerShape(16.dp), color = TracklyTheme.extras.premiumContainer) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = TracklyTheme.extras.premium)
            Text(
                text = stringResource(reasonText(feature)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun reasonText(feature: PremiumFeature): Int = when (feature) {
    PremiumFeature.UNLIMITED_HABITS -> R.string.premium_reason_habits
    PremiumFeature.UNLIMITED_REMINDERS -> R.string.premium_reason_reminders
    PremiumFeature.CUSTOM_COLORS -> R.string.premium_reason_colors
    PremiumFeature.PREMIUM_THEMES -> R.string.premium_reason_themes
    PremiumFeature.ADVANCED_CALENDAR -> R.string.premium_reason_calendar
    PremiumFeature.FULL_HISTORY -> R.string.premium_reason_history
    PremiumFeature.ADVANCED_STATISTICS, PremiumFeature.ADVANCED_CHARTS -> R.string.premium_reason_stats
    PremiumFeature.PREMIUM_INSIGHTS -> R.string.premium_reason_insights
    PremiumFeature.DATA_EXPORT -> R.string.premium_reason_export
    PremiumFeature.HOME_WIDGET -> R.string.premium_reason_widget
}

@Composable
private fun Features() {
    val items = listOf(
        R.string.premium_feature_habits,
        R.string.premium_feature_widget,
        R.string.premium_feature_stats,
        R.string.premium_feature_reminders,
        R.string.premium_feature_themes,
        R.string.premium_feature_history,
        R.string.premium_feature_colors,
        R.string.premium_feature_calendar,
        R.string.premium_feature_insights,
        R.string.premium_feature_export,
    )
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items.forEach { res ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(stringResource(res), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PlansCard(
    state: ProductState,
    canPurchase: Boolean,
    onPurchase: (PremiumPlan) -> Unit,
    onRetry: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val available = state as? ProductState.Available
    val selected = available?.let { s -> s.plans.firstOrNull { it.id == selectedId } ?: s.defaultPlan }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("price_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is ProductState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(stringResource(R.string.premium_loading_price), style = MaterialTheme.typography.bodyMedium)
                }
                is ProductState.Unavailable -> {
                    Text(
                        text = stringResource(state.reason.messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("premium_retry")) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                is ProductState.Available -> {
                    val savings = PlanPricing.yearlySavingsPercent(state.plans)
                    Column(
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.plans.forEach { plan ->
                            PlanOption(
                                plan = plan,
                                selected = plan.id == selected?.id,
                                savingsPercent = savings.takeIf { plan.kind == PlanKind.YEARLY },
                                onClick = { selectedId = plan.id },
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { selected?.let(onPurchase) },
                enabled = selected != null && canPurchase,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_premium"),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(purchaseButtonText(selected), style = MaterialTheme.typography.titleMedium)
            }
            val trialDays = selected?.freeTrialDays
            if (selected?.kind == PlanKind.LIFETIME) {
                Text(
                    text = stringResource(R.string.premium_lifetime_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            } else if (selected != null && trialDays != null) {
                Text(
                    text = stringResource(R.string.premium_trial_then, trialDays, priceLine(selected)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("premium_trial_terms"),
                )
            }
        }
    }
}

@Composable
private fun PlanOption(
    plan: PremiumPlan,
    selected: Boolean,
    savingsPercent: Int?,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag("plan_${plan.kind.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(planTitle(plan.kind)),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    when {
                        savingsPercent != null -> PlanBadge(stringResource(R.string.premium_plan_save, savingsPercent))
                        plan.kind == PlanKind.LIFETIME -> PlanBadge(stringResource(R.string.premium_plan_forever_badge))
                    }
                }
                val detail = when (plan.kind) {
                    PlanKind.YEARLY -> PlanPricing.monthlyEquivalent(plan, locale)
                        ?.let { stringResource(R.string.premium_plan_per_month, it) }
                    PlanKind.LIFETIME -> stringResource(R.string.premium_plan_one_time)
                    else -> null
                }
                val trial = plan.freeTrialDays?.let { stringResource(R.string.premium_trial, it) }
                listOfNotNull(detail, trial).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = shortPrice(plan),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.testTag("premium_price"),
            )
        }
    }
}

@Composable
private fun PlanBadge(text: String) {
    Surface(shape = RoundedCornerShape(50), color = TracklyTheme.extras.premium) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun planTitle(kind: PlanKind): Int = when (kind) {
    PlanKind.YEARLY -> R.string.premium_plan_yearly
    PlanKind.MONTHLY -> R.string.premium_plan_monthly
    PlanKind.LIFETIME -> R.string.premium_plan_lifetime
    PlanKind.OTHER -> R.string.premium_plan_other
}

@Composable
private fun purchaseButtonText(plan: PremiumPlan?): String {
    val trialDays = plan?.freeTrialDays
    return when {
        plan?.kind == PlanKind.LIFETIME -> stringResource(R.string.premium_buy_lifetime)
        trialDays != null -> stringResource(R.string.premium_start_trial, trialDays)
        else -> stringResource(R.string.premium_start)
    }
}

/** "$19.99 / year" or "$39.99" — amount and currency always come from Google Play. */
@Composable
private fun shortPrice(plan: PremiumPlan): String {
    val periodText = periodText(plan.billingPeriod)
    return if (periodText.isEmpty()) plan.formattedPrice else stringResource(R.string.price_per_period, plan.formattedPrice, periodText)
}

/** "Premium — $2.99 / month". */
@Composable
private fun priceLine(plan: PremiumPlan): String {
    val periodText = periodText(plan.billingPeriod)
    return if (periodText.isEmpty()) {
        stringResource(R.string.premium_price_only, plan.formattedPrice)
    } else {
        stringResource(R.string.premium_price_period, plan.formattedPrice, periodText)
    }
}

@Composable
private fun periodText(isoPeriod: String?): String {
    val period = isoPeriod?.let { BillingPeriod.parse(it) } ?: return ""
    return when {
        period.unit == PeriodUnit.MONTH && period.count == 1 -> stringResource(R.string.period_month)
        period.unit == PeriodUnit.YEAR && period.count == 1 -> stringResource(R.string.period_year)
        period.unit == PeriodUnit.WEEK && period.count == 1 -> stringResource(R.string.period_week)
        period.unit == PeriodUnit.MONTH -> stringResource(R.string.period_months, period.count)
        period.unit == PeriodUnit.YEAR -> stringResource(R.string.period_years, period.count)
        period.unit == PeriodUnit.WEEK -> stringResource(R.string.period_weeks, period.count)
        else -> stringResource(R.string.period_days, period.count)
    }
}

@Composable
private fun ActiveCard(canceled: Boolean, lifetime: Boolean, onManage: () -> Unit) {
    AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = TracklyTheme.extras.successContainer,
            modifier = Modifier.fillMaxWidth().testTag("premium_active"),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("🎉", style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.premium_active_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                val message = when {
                    lifetime -> R.string.premium_active_lifetime
                    canceled -> R.string.premium_active_canceled
                    else -> R.string.premium_active_message
                }
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!lifetime) {
                    OutlinedButton(onClick = onManage, modifier = Modifier.testTag("premium_manage")) {
                        Text(stringResource(R.string.settings_manage_subscription))
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingBanner() {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = stringResource(R.string.premium_pending_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun Disclosure(hasLifetime: Boolean) {
    val text = stringResource(R.string.premium_disclosure) +
        if (hasLifetime) " " + stringResource(R.string.premium_disclosure_lifetime) else ""
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().testTag("premium_disclosure"),
    )
}

@Composable
private fun LegalLinks(onOpenLegal: (LegalKind) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = {
                if (BuildConfig.TERMS_OF_SERVICE_URL.isBlank() || !openUrl(context, BuildConfig.TERMS_OF_SERVICE_URL)) {
                    onOpenLegal(LegalKind.TERMS)
                }
            },
            modifier = Modifier.testTag("premium_terms"),
        ) { Text(stringResource(R.string.settings_terms)) }
        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(
            onClick = {
                if (BuildConfig.PRIVACY_POLICY_URL.isBlank() || !openUrl(context, BuildConfig.PRIVACY_POLICY_URL)) {
                    onOpenLegal(LegalKind.PRIVACY)
                }
            },
            modifier = Modifier.testTag("premium_privacy"),
        ) { Text(stringResource(R.string.settings_privacy_policy)) }
    }
}
