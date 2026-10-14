@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.trackly.presentation.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.trackly.R
import com.example.trackly.presentation.settings.LegalKind
import com.example.trackly.ui.components.CenteredContent

/**
 * Placeholder legal screens. Replace the TODO text in `res/values/legal.xml` — or set
 * PRIVACY_POLICY_URL / TERMS_OF_SERVICE_URL in gradle.properties to open your hosted pages instead.
 */
@Composable
fun LegalScreen(kind: LegalKind, onBack: () -> Unit) {
    val title = if (kind == LegalKind.PRIVACY) R.string.settings_privacy_policy else R.string.settings_terms
    val body = if (kind == LegalKind.PRIVACY) R.string.legal_privacy_body else R.string.legal_terms_body
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .testTag("legal_body"),
            ) {
                Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
