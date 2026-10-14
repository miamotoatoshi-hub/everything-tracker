@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.premium

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.example.trackly.billing.PremiumProduct
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
                        onManage = { openUrl(context, BillingConfig.manageSubscriptionUrl(context.packageName)) },
                    )
                    else -> {
                        if (entitlement.status == EntitlementStatus.PENDING) PendingBanner()
                        PriceCard(
                            state = productState,
                            canPurchase = entitlement.status != EntitlementStatus.PENDING,
                            onPurchase = { context.findActivity()?.let(viewModel::purchase) },
                            onRetry = viewModel::retry,
                        )
                    }
                }

                TextButton(
                    onClick = viewModel::restore,
                    modifier = Modifier.align(Alignment.CenterHorizontally).testTag("restore_purchases_button"),
                ) { Text(stringResource(R.string.settings_restore_purchases)) }

                Disclosure()
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
}

@Composable
private fun Features() {
    val items = listOf(
        R.string.premium_feature_habits,
        R.string.premium_feature_stats,
        R.string.premium_feature_reminders,
        R.string.premium_feature_themes,
        R.string.premium_feature_history,
        R.string.premium_feature_colors,
        R.string.premium_feature_calendar,
        R.string.premium_feature_insights,
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
private fun PriceCard(
    state: ProductState,
    canPurchase: Boolean,
    onPurchase: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("price_card"),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    val product = state.product
                    Text(
                        text = priceLine(product),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("premium_price"),
                    )
                    product.freeTrialDays?.let { days ->
                        Text(
                            text = stringResource(R.string.premium_trial, days),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Button(
                onClick = onPurchase,
                enabled = state is ProductState.Available && canPurchase,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_premium"),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(stringResource(R.string.premium_start), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** "Premium — $2.99 / month". The amount and currency always come from Google Play. */
@Composable
private fun priceLine(product: PremiumProduct): String {
    val period = BillingPeriod.parse(product.billingPeriod)
    val periodText = when {
        period == null -> ""
        period.unit == PeriodUnit.MONTH && period.count == 1 -> stringResource(R.string.period_month)
        period.unit == PeriodUnit.YEAR && period.count == 1 -> stringResource(R.string.period_year)
        period.unit == PeriodUnit.WEEK && period.count == 1 -> stringResource(R.string.period_week)
        period.unit == PeriodUnit.MONTH -> stringResource(R.string.period_months, period.count)
        period.unit == PeriodUnit.YEAR -> stringResource(R.string.period_years, period.count)
        period.unit == PeriodUnit.WEEK -> stringResource(R.string.period_weeks, period.count)
        else -> stringResource(R.string.period_days, period.count)
    }
    return if (periodText.isEmpty()) {
        stringResource(R.string.premium_price_only, product.formattedPrice)
    } else {
        stringResource(R.string.premium_price_period, product.formattedPrice, periodText)
    }
}

@Composable
private fun ActiveCard(canceled: Boolean, onManage: () -> Unit) {
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
                Text(
                    stringResource(if (canceled) R.string.premium_active_canceled else R.string.premium_active_message),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onManage, modifier = Modifier.testTag("premium_manage")) {
                    Text(stringResource(R.string.settings_manage_subscription))
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
private fun Disclosure() {
    Text(
        text = stringResource(R.string.premium_disclosure),
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
