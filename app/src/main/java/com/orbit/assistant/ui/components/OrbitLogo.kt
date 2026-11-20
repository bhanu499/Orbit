package com.orbit.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.assistant.ui.theme.OrbitMotion
import com.orbit.assistant.ui.theme.OrbitTheme

enum class AgentHeaderStatus(val label: String) {
    READY("Ready"),
    WORKING("Working"),
    PAUSED("Paused")
}

@Composable
fun OrbitLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val primary = OrbitTheme.colors.accentPrimary
    val secondary = OrbitTheme.colors.accentSecondary
    val dot = OrbitTheme.colors.textPrimary

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Orbit logo" }
    ) {
        val strokeWidth = this.size.minDimension * 0.11f
        val radius = (this.size.minDimension - strokeWidth) / 2f
        drawCircle(
            color = primary,
            radius = radius,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawCircle(
            color = secondary,
            radius = radius * 0.36f
        )
        drawCircle(
            color = dot,
            radius = radius * 0.16f,
            center = Offset(
                x = center.x + radius * 0.68f,
                y = center.y - radius * 0.68f
            )
        )
    }
}

@Composable
fun OrbitStatusPill(
    status: AgentHeaderStatus,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val targetColor by animateColorAsState(
        targetValue = when (status) {
            AgentHeaderStatus.READY -> colors.statusSuccess
            AgentHeaderStatus.WORKING -> colors.accentPrimary
            AgentHeaderStatus.PAUSED -> colors.statusWarning
        },
        animationSpec = OrbitMotion.fastTween(),
        label = "statusColor"
    )
    val bgColor by animateColorAsState(
        targetValue = when (status) {
            AgentHeaderStatus.READY -> colors.statusSuccessContainer
            AgentHeaderStatus.WORKING -> colors.accentContainer
            AgentHeaderStatus.PAUSED -> colors.statusWarningContainer
        },
        animationSpec = OrbitMotion.fastTween(),
        label = "statusBg"
    )

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .border(1.dp, targetColor.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (status == AgentHeaderStatus.WORKING) {
                        targetColor.copy(alpha = pulseAlpha)
                    } else targetColor
                )
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = targetColor
        )
    }
}
