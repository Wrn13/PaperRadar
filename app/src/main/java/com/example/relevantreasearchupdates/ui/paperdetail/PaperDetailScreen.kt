package com.example.relevantreasearchupdates.ui.paperdetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.relevantreasearchupdates.data.PaperSource
import com.example.relevantreasearchupdates.util.EzproxyUrlBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperDetailScreen(
    paperId: Long,
    onReadPdf: (Long) -> Unit,
    viewModel: PaperDetailViewModel = viewModel()
) {
    LaunchedEffect(paperId) { viewModel.load(paperId) }

    val paper by viewModel.paper.collectAsState()
    val ezproxyTemplate by viewModel.ezproxyUrlTemplate.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paper details") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        val currentPaper = paper
        if (currentPaper == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val label = if (currentPaper.source == PaperSource.ARXIV) "arXiv" else "Journal"
            val icon = if (currentPaper.source == PaperSource.ARXIV) Icons.AutoMirrored.Filled.Article else Icons.AutoMirrored.Filled.MenuBook
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.padding(0.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                border = null
            )

            Text(
                text = currentPaper.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = currentPaper.authors,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            val subtitle = listOfNotNull(
                currentPaper.journal,
                currentPaper.publishedDate.ifBlank { null }
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = "Matched: ${currentPaper.matchedQuery}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (currentPaper.summary.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Abstract", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = currentPaper.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(top = 20.dp)) {
                if (currentPaper.pdfUrl != null) {
                    Button(
                        onClick = { onReadPdf(currentPaper.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(if (currentPaper.source == PaperSource.ARXIV) "Read PDF (arXiv)" else "Read PDF")
                    }
                } else {
                    Text(
                        "No direct PDF is available" +
                            (if (currentPaper.source == PaperSource.CROSSREF) " — this paper is published in a journal, likely behind a paywall." else "."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                // arXiv is open access, so it never goes through the proxy. Journal "PDF" links
                // from Crossref are frequently publisher links that still need a subscription,
                // so keep the proxy route available for those even when one exists.
                if (currentPaper.source != PaperSource.ARXIV) {
                    val proxyButtonModifier = if (currentPaper.pdfUrl != null) {
                        Modifier.fillMaxWidth().padding(top = 12.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    Button(
                        onClick = {
                            val target = currentPaper.doi?.let { "https://doi.org/$it" } ?: currentPaper.sourceUrl
                            val proxied = EzproxyUrlBuilder.build(ezproxyTemplate, target)
                            openInBrowser(context, proxied)
                        },
                        modifier = proxyButtonModifier
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Open via EZproxy")
                    }
                    if (ezproxyTemplate.isBlank()) {
                        Text(
                            "Tip: set your institution's EZproxy URL in Settings to unlock subscribed journal content.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = { openInBrowser(context, currentPaper.sourceUrl) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text("Open publisher page (no proxy)")
                }
            }
        }
    }
}

private fun openInBrowser(context: android.content.Context, url: String) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
