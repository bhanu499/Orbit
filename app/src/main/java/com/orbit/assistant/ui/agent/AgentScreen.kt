package com.orbit.assistant.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.assistant.domain.models.MessageRole
import com.orbit.assistant.domain.state.AgentState
import com.orbit.assistant.ui.components.AgentHeaderStatus
import com.orbit.assistant.ui.components.HumanApprovalCard
import com.orbit.assistant.ui.components.MarkdownText
import com.orbit.assistant.ui.components.OrbitCard
import com.orbit.assistant.ui.components.OrbitLogoMark
import com.orbit.assistant.ui.components.OrbitSecondaryButton
import com.orbit.assistant.ui.components.OrbitStatusPill
import com.orbit.assistant.ui.components.TaskExecutionCard
import com.orbit.assistant.ui.theme.OrbitTheme

private data class SuggestedPrompt(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

private val suggestedPrompts = listOf(
    SuggestedPrompt(
        title = "Find the cheapest groceries for my list",
        subtitle = "Compare Blinkit & Zepto baskets",
        icon = Icons.Rounded.ShoppingCart
    ),
    SuggestedPrompt(
        title = "Compare prices across shopping apps",
        subtitle = "Check milk prices in Demo Mode",
        icon = Icons.Rounded.CompareArrows
    ),
    SuggestedPrompt(
        title = "Organize my day",
        subtitle = "Plan focus blocks & errands",
        icon = Icons.Rounded.CalendarToday
    ),
    SuggestedPrompt(
        title = "Find and book something for me",
        subtitle = "Test user intervention & handoff",
        icon = Icons.Rounded.TravelExplore
    )
)

@Composable
fun AgentScreen(
    onOpenSettings: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = OrbitTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AgentHeader(
                status = state.headerStatus,
                onSettingsClick = onOpenSettings
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            if (state.messages.isEmpty()) {
                EmptyAgentWelcome(
                    supportedAppNames = state.discoveredApps.filter { it.isSupported }.map { it.appName },
                    onPromptClick = { prompt ->
                        focusManager.clearFocus()
                        viewModel.submitPrompt(prompt)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.messages, key = { it.messageId }) { message ->
                        if (message.role == MessageRole.USER) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 310.dp)
                                        .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                                        .background(colors.userBubble)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.userBubbleText
                                    )
                                }
                            }
                        } else {
                            val linkedTask = message.linkedTaskId?.let { state.activeTasksById[it] }
                            if (linkedTask != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TaskExecutionCard(
                                        task = linkedTask,
                                        onPause = { viewModel.pauseActiveTask(linkedTask.taskId) },
                                        onResume = { viewModel.resumeActiveTask(linkedTask.taskId) },
                                        onStop = { viewModel.stopActiveTask(linkedTask.taskId) },
                                        onTakeOver = { viewModel.takeOverTask(linkedTask.taskId) },
                                        onContinue = { viewModel.continueAfterIntervention(linkedTask.taskId) }
                                    )
                                    val approval = linkedTask.approvalRequired
                                    if (linkedTask.status is AgentState.WaitingForApproval && approval != null) {
                                        HumanApprovalCard(
                                            approval = approval,
                                            onApproveAndContinue = {
                                                viewModel.approveTask(linkedTask.taskId, approval.approvalId)
                                            },
                                            onReject = {
                                                viewModel.rejectTask(linkedTask.taskId, approval.approvalId)
                                            },
                                            onOpenApp = { pkg -> viewModel.openExternalApp(pkg) },
                                            onAdjustItemQuantity = { itemId, delta ->
                                                viewModel.adjustBasketQuantity(linkedTask.taskId, itemId, delta)
                                            }
                                        )
                                    }
                                    val actionPkg = linkedTask.result?.primaryActionPackage
                                    val actionLabel = linkedTask.result?.primaryActionLabel
                                    if (linkedTask.status is AgentState.Completed && actionPkg != null && actionLabel != null) {
                                        OrbitSecondaryButton(
                                            onClick = { viewModel.openExternalApp(actionPkg) }
                                        ) {
                                            Icon(Icons.Rounded.Launch, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(actionLabel)
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    OrbitCard(
                                        modifier = Modifier.widthIn(max = 330.dp),
                                        containerColor = colors.surface,
                                        borderColor = colors.borderSubtle
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            MarkdownText(markdown = message.text)
                                            if (message.isStreaming) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "•••",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.accentPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.isTyping) {
                        item(key = "typing_indicator") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.accentPrimary
                                )
                                Text(
                                    text = "Orbit is thinking…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            ChatComposer(
                text = state.inputText,
                isWorking = state.headerStatus == AgentHeaderStatus.WORKING,
                onTextChange = viewModel::onInputChanged,
                onSend = {
                    focusManager.clearFocus()
                    viewModel.submitPrompt()
                },
                onStop = viewModel::stopCurrentWorkingTask,
                onAttachClick = viewModel::onAttachmentPlaceholderClicked,
                onVoiceClick = viewModel::onVoicePlaceholderClicked
            )
        }
    }
}

@Composable
private fun AgentHeader(
    status: AgentHeaderStatus,
    onSettingsClick: () -> Unit
) {
    val colors = OrbitTheme.colors
    Column(modifier = Modifier.background(colors.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OrbitLogoMark(size = 28.dp)
                Text(
                    text = "Orbit",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary
                )
                OrbitStatusPill(status = status)
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.semantics { contentDescription = "Open Settings" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = colors.textSecondary
                )
            }
        }
        HorizontalDivider(color = colors.borderSubtle)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyAgentWelcome(
    supportedAppNames: List<String>,
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accentContainer)
                    .border(1.dp, colors.accentPrimary.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                OrbitLogoMark(size = 32.dp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your apps. One agent.",
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tell Orbit what you need. I'll take care of the steps.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }

        if (supportedAppNames.isNotEmpty()) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    supportedAppNames.take(5).forEach { appName ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.surfaceElevated)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(suggestedPrompts, key = { it.title }) { prompt ->
            OrbitCard(
                onClick = { onPromptClick(prompt.title) },
                containerColor = colors.surface,
                borderColor = colors.borderSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = prompt.icon,
                            contentDescription = null,
                            tint = colors.accentPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = prompt.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        Text(
                            text = prompt.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    text: String,
    isWorking: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    val colors = OrbitTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.semantics { contentDescription = "Attach file" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "Attach",
                    tint = colors.textSecondary
                )
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = "Ask Orbit to do something…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textTertiary
                    )
                },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accentPrimary
                )
            )

            IconButton(
                onClick = onVoiceClick,
                modifier = Modifier.semantics { contentDescription = "Voice input" }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Voice",
                    tint = colors.textSecondary
                )
            }

            AnimatedVisibility(visible = isWorking, enter = fadeIn(), exit = fadeOut()) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.statusErrorContainer)
                        .semantics { contentDescription = "Stop execution" }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Stop",
                        tint = colors.statusError
                    )
                }
            }

            if (!isWorking) {
                IconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank(),
                    modifier = Modifier
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (text.isNotBlank()) colors.accentPrimary else colors.surfaceElevated
                        )
                        .semantics { contentDescription = "Send prompt" }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Send,
                        contentDescription = "Send",
                        tint = if (text.isNotBlank()) Color.White else colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
