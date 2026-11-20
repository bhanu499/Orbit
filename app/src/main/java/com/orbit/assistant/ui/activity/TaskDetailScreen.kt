package com.orbit.assistant.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.assistant.ui.components.ExecutionEventRow
import com.orbit.assistant.ui.components.MarkdownText
import com.orbit.assistant.ui.components.OrbitCard
import com.orbit.assistant.ui.components.TaskStatusBadge
import com.orbit.assistant.ui.components.formatDuration
import com.orbit.assistant.ui.theme.OrbitTheme

@Composable
fun TaskDetailScreen(
    taskId: String,
    onBack: () -> Unit,
    viewModel: ActivityViewModel = hiltViewModel()
) {
    val taskFlow = remember(taskId) { viewModel.observeTaskDetail(taskId) }
    val task by taskFlow.collectAsStateWithLifecycle()
    val colors = OrbitTheme.colors

    Scaffold(
        containerColor = colors.background,
        topBar = {
            Column(modifier = Modifier.background(colors.background)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                    Text(
                        text = "Task details",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                }
                HorizontalDivider(color = colors.borderSubtle)
            }
        }
    ) { innerPadding ->
        val currentTask = task
        if (currentTask == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.accentPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Original instruction
                item {
                    OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TaskStatusBadge(status = currentTask.status)
                                Text(
                                    text = "${formatTimestamp(currentTask.createdAt)} • ${formatDuration(currentTask.durationMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Instruction",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textTertiary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentTask.userPrompt,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                // Timeline
                if (currentTask.events.isNotEmpty()) {
                    item {
                        Text(
                            text = "Actions & Observations",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textSecondary
                        )
                    }
                    itemsIndexed(currentTask.events, key = { _, e -> e.eventId }) { idx, event ->
                        ExecutionEventRow(
                            event = event,
                            isLast = idx == currentTask.events.lastIndex
                        )
                    }
                }

                // Result
                currentTask.result?.let { result ->
                    item {
                        OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Result",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.accentPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                MarkdownText(markdown = result.summaryMarkdown)
                            }
                        }
                    }
                }

                // Errors
                currentTask.error?.let { err ->
                    item {
                        OrbitCard(
                            containerColor = colors.statusErrorContainer,
                            borderColor = colors.statusError.copy(alpha = 0.45f)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Error",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.statusError
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // Approval history
                if (currentTask.approvalHistory.isNotEmpty()) {
                    item {
                        OrbitCard(containerColor = colors.surface, borderColor = colors.borderSubtle) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Approval history",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textTertiary
                                )
                                currentTask.approvalHistory.forEach { entry ->
                                    Column {
                                        Text(
                                            text = "${entry.title} — ${entry.decision.name}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "${entry.summary} (${formatTimestamp(entry.timestamp)})",
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
        }
    }
}
