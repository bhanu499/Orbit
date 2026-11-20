package com.orbit.assistant.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddShoppingCart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.assistant.domain.models.AgentEvent
import com.orbit.assistant.domain.models.EventStatus
import com.orbit.assistant.domain.models.EventType
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.state.AgentState
import com.orbit.assistant.ui.theme.OrbitMotion
import com.orbit.assistant.ui.theme.OrbitTheme
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun TaskExecutionCard(
    task: Task,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onTakeOver: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    var liveElapsedMs by remember(task.taskId, task.status) {
        mutableLongStateOf(
            if (task.status.isTerminal) task.durationMs
            else (System.currentTimeMillis() - task.createdAt).coerceAtLeast(0L)
        )
    }

    LaunchedEffect(task.taskId, task.status) {
        while (task.status is AgentState.Running || task.status is AgentState.Planning) {
            liveElapsedMs = (System.currentTimeMillis() - task.createdAt).coerceAtLeast(0L)
            delay(500L)
        }
        if (task.durationMs > 0L) {
            liveElapsedMs = task.durationMs
        }
    }

    OrbitCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surface,
        borderColor = when (task.status) {
            is AgentState.NeedsUserIntervention -> colors.statusWarning.copy(alpha = 0.5f)
            is AgentState.WaitingForApproval -> colors.accentPrimary.copy(alpha = 0.55f)
            is AgentState.Failed -> colors.statusError.copy(alpha = 0.45f)
            else -> colors.border
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Task Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaskStatusBadge(status = task.status)
                        Text(
                            text = formatDuration(liveElapsedMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textTertiary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = task.userPrompt,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (task.status.isActive) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.semantics { contentDescription = "Stop agent" }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop",
                            tint = colors.statusError
                        )
                    }
                }
            }

            if (task.events.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.borderSubtle)
                Spacer(modifier = Modifier.height(10.dp))

                // Live execution timeline
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.events.forEachIndexed { index, event ->
                        ExecutionEventRow(
                            event = event,
                            isLast = index == task.events.lastIndex
                        )
                    }
                }
            }

            // Execution controls
            if (task.status.isActive) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.borderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (task.status) {
                        is AgentState.Paused -> {
                            OrbitPrimaryButton(
                                onClick = onResume,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Resume")
                            }
                        }
                        is AgentState.NeedsUserIntervention -> {
                            OrbitPrimaryButton(
                                onClick = onContinue,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Continue")
                            }
                            OrbitSecondaryButton(
                                onClick = onTakeOver,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.TouchApp, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Take over")
                            }
                        }
                        is AgentState.Running, is AgentState.Planning -> {
                            OrbitSecondaryButton(
                                onClick = onPause,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Pause, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pause")
                            }
                            OrbitSecondaryButton(
                                onClick = onTakeOver,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.PanTool, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Take over")
                            }
                        }
                        else -> Unit
                    }

                    if (task.status !is AgentState.WaitingForApproval) {
                        OrbitSecondaryButton(
                            onClick = onStop,
                            borderColor = colors.statusError.copy(alpha = 0.45f),
                            contentColor = colors.statusError
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExecutionEventRow(
    event: AgentEvent,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    var expanded by remember(event.eventId) { mutableStateOf(false) }

    val statusColor by animateColorAsState(
        targetValue = when (event.status) {
            EventStatus.PENDING -> colors.textTertiary
            EventStatus.IN_PROGRESS -> colors.accentPrimary
            EventStatus.COMPLETED -> colors.statusSuccess
            EventStatus.FAILED -> colors.statusError
            EventStatus.NEEDS_ATTENTION -> colors.statusWarning
        },
        animationSpec = OrbitMotion.fastTween(),
        label = "eventStatusColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (expanded) colors.surfaceElevated.copy(alpha = 0.7f)
                else Color.Transparent
            )
            .clickable { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = event.status,
                    transitionSpec = { fadeIn(OrbitMotion.fastTween()) togetherWith fadeOut(OrbitMotion.fastTween()) },
                    label = "eventStatusIcon"
                ) { st ->
                    when (st) {
                        EventStatus.IN_PROGRESS -> CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentPrimary
                        )
                        EventStatus.COMPLETED -> Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Completed",
                            tint = colors.statusSuccess,
                            modifier = Modifier.size(17.dp)
                        )
                        EventStatus.FAILED -> Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Failed",
                            tint = colors.statusError,
                            modifier = Modifier.size(17.dp)
                        )
                        EventStatus.NEEDS_ATTENTION -> Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Needs attention",
                            tint = colors.statusWarning,
                            modifier = Modifier.size(17.dp)
                        )
                        EventStatus.PENDING -> Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.textTertiary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = eventTypeIcon(event.eventType),
                contentDescription = null,
                tint = statusColor.copy(alpha = 0.9f),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (event.status == EventStatus.IN_PROGRESS) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (event.status == EventStatus.PENDING) colors.textSecondary else colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Collapse details" else "Expand details",
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(OrbitMotion.expandSpring) + fadeIn(OrbitMotion.fastTween()),
            exit = shrinkVertically() + fadeOut(OrbitMotion.fastTween())
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 32.dp, top = 6.dp, end = 4.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                if (event.metadata.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        event.metadata.entries.take(3).forEach { (k, v) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surfaceSubtle)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$k: $v",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TaskStatusBadge(status: AgentState, modifier: Modifier = Modifier) {
    val colors = OrbitTheme.colors
    val (label, textColor, bgColor) = when (status) {
        AgentState.Idle -> Triple("Idle", colors.textSecondary, colors.surfaceSubtle)
        AgentState.Planning -> Triple("Planning", colors.accentPrimary, colors.accentContainer)
        AgentState.Running -> Triple("In progress", colors.accentPrimary, colors.accentContainer)
        AgentState.Paused -> Triple("Paused", colors.statusWarning, colors.statusWarningContainer)
        AgentState.WaitingForApproval -> Triple("Awaiting approval", colors.accentSecondary, colors.statusInfoContainer)
        AgentState.NeedsUserIntervention -> Triple("Needs attention", colors.statusWarning, colors.statusWarningContainer)
        AgentState.Completed -> Triple("Completed", colors.statusSuccess, colors.statusSuccessContainer)
        AgentState.Failed -> Triple("Failed", colors.statusError, colors.statusErrorContainer)
        AgentState.Cancelled -> Triple("Stopped", colors.textSecondary, colors.surfaceSubtle)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

private fun eventTypeIcon(type: EventType): ImageVector = when (type) {
    EventType.Thinking -> Icons.Rounded.Psychology
    is EventType.AppLaunched -> Icons.Rounded.Launch
    is EventType.ScreenObserved -> Icons.Rounded.Visibility
    is EventType.SearchPerformed -> Icons.Rounded.Search
    is EventType.ProductIdentified -> Icons.Rounded.ShoppingBag
    is EventType.ItemAdded -> Icons.Rounded.AddShoppingCart
    is EventType.Verification -> Icons.Rounded.Verified
    is EventType.Error -> Icons.Rounded.ErrorOutline
    is EventType.UserInterventionRequired -> Icons.Rounded.WarningAmber
}

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return if (mins > 0) String.format(Locale.US, "%dm %02ds", mins, secs)
    else String.format(Locale.US, "%ds", secs)
}
