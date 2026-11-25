@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.trackly.BuildConfig
import com.example.trackly.R
import com.example.trackly.presentation.settings.LegalKind
import com.example.trackly.ui.components.CenteredContent

/**
 * The bundled Privacy Policy and Terms (res/values*/legal.xml). The developer name, contact e-mail and
 * effective date come from gradle.properties (DEVELOPER_NAME, SUPPORT_EMAIL, LEGAL_EFFECTIVE_DATE).
 * When PRIVACY_POLICY_URL / TERMS_OF_SERVICE_URL are set, Settings opens those pages instead.
 */
@Composable
fun LegalScreen(kind: LegalKind, onBack: () -> Unit) {
    val title = if (kind == LegalKind.PRIVACY) R.string.settings_privacy_policy else R.string.settings_terms
    val bodyRes = if (kind == LegalKind.PRIVACY) R.string.legal_privacy_body else R.string.legal_terms_body
    val locale = LocalConfiguration.current.locales[0]
    val developer = BuildConfig.DEVELOPER_NAME.ifBlank { stringResource(R.string.legal_developer_fallback) }
    val contact = if (BuildConfig.SUPPORT_EMAIL.isNotBlank()) {
        stringResource(R.string.legal_contact_email, BuildConfig.SUPPORT_EMAIL)
    } else {
        stringResource(R.string.legal_contact_fallback)
    }
    val date = LegalText.effectiveDate(BuildConfig.LEGAL_EFFECTIVE_DATE, locale)
    val body = stringResource(bodyRes, developer, contact, date)
    val blocks = remember(body) { LegalText.parse(body) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        CenteredContent(modifier = Modifier.padding(padding), maxWidth = 640.dp) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("legal_body"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    blocks.forEach { block ->
                        when (block) {
                            is LegalBlock.Heading -> Text(
                                text = block.text,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                            )
                            is LegalBlock.Paragraph -> Text(
                                text = linkified(block.text),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Makes web and e-mail addresses tappable. */
@Composable
private fun linkified(text: String): AnnotatedString {
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    return remember(text, linkStyle) {
        buildAnnotatedString {
            append(text)
            LegalText.links(text).forEach { (range, _, uri) ->
                addLink(LinkAnnotation.Url(uri, linkStyle), range.first, range.last + 1)
            }
        }
    }
}
