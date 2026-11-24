package com.orbit.assistant.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.assistant.ui.components.OrbitCard
import com.orbit.assistant.ui.theme.OrbitTheme
import java.util.Locale

@Composable
fun UsageScreen(
    onBack: () -> Unit,
    viewModel: UsageViewModel = hiltViewModel()
) {
    val summary by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = OrbitTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Column(modifier = Modifier.background(colors.background)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                        Text(
                            text = "Token & Cost Usage",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                    }
                    if (summary.totalRequests > 0) {
                        IconButton(onClick = viewModel::clearUsage) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear usage logs",
                                tint = colors.statusError
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.borderSubtle)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        MetricRow("Total tokens", "${summary.totalTokens} (in: ${summary.totalInputTokens}, out: ${summary.totalOutputTokens}, cached: ${summary.totalCachedTokens})")
                        MetricRow("Total estimated cost", formatUsd(summary.totalEstimatedCostUsd))
                        MetricRow("Avg cost per task", formatUsd(summary.averageCostPerTaskUsd))
                        MetricRow("Avg tokens per action", "${summary.averageTokensPerAction}")
                        MetricRow("Requests / Tasks", "${summary.totalRequests} calls across ${summary.distinctTasks} tasks")
                    }
                }
            }

            if (summary.providerBreakdown.isNotEmpty()) {
                item {
                    Text(
                        text = "Provider breakdown",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textTertiary
                    )
                }
                items(summary.providerBreakdown, key = { it.provider }) { prov ->
                    OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(prov.provider, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                Text(formatUsd(prov.estimatedCostUsd), style = MaterialTheme.typography.titleSmall, color = colors.accentPrimary)
                            }
                            Text(
                                text = "${prov.requestCount} calls • in ${prov.inputTokens} / out ${prov.outputTokens} / cached ${prov.cachedTokens}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            if (summary.taskBreakdown.isNotEmpty()) {
                item {
                    Text(
                        text = "Cost per task",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textTertiary
                    )
                }
                items(summary.taskBreakdown, key = { it.taskId }) { taskCost ->
                    OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(taskCost.taskId, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                Text(
                                    text = "${taskCost.requestCount} actions • ${taskCost.totalTokens} tokens",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Text(
                                text = formatUsd(taskCost.estimatedCostUsd),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.accentPrimary
                            )
                        }
                    }
                }
            }

            if (summary.totalRequests == 0) {
                item {
                    OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "No LLM usage recorded yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Run a Live Multi-LLM task to see token counts, latency, and estimated cost per provider and task.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = OrbitTheme.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, color = OrbitTheme.colors.textPrimary)
    }
}

private fun formatUsd(amount: Double): String = String.format(Locale.US, "$%.5f", amount)
