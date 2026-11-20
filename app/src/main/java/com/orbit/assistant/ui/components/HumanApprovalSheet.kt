package com.orbit.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.assistant.domain.models.ApprovalRequest
import com.orbit.assistant.domain.models.BasketItem
import com.orbit.assistant.domain.models.StoreComparisonOption
import com.orbit.assistant.ui.theme.OrbitTheme
import kotlin.math.roundToInt

@Composable
fun HumanApprovalCard(
    approval: ApprovalRequest,
    onApproveAndContinue: () -> Unit,
    onReject: () -> Unit,
    onOpenApp: (String) -> Unit,
    onAdjustItemQuantity: (itemId: String, delta: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val shopping = approval.shoppingData
    var isEditingBasket by remember { mutableStateOf(false) }

    OrbitCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.surface,
        borderColor = colors.accentPrimary.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accentContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ShoppingBasket,
                        contentDescription = null,
                        tint = colors.accentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = approval.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = approval.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            if (shopping != null) {
                Spacer(modifier = Modifier.height(14.dp))

                // Store badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storefront,
                        contentDescription = null,
                        tint = colors.accentSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Store: ${shopping.storeName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Product rows
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shopping.items.forEach { item ->
                        ShoppingItemRow(
                            item = item,
                            isEditing = isEditingBasket,
                            onIncrement = { onAdjustItemQuantity(item.itemId, 1) },
                            onDecrement = { onAdjustItemQuantity(item.itemId, -1) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.borderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                // Basket summary
                Text(
                    text = "Basket summary",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                SummaryLine("Items", "${shopping.summary.itemCount}")
                SummaryLine("Subtotal", formatInr(shopping.summary.subtotal))
                SummaryLine("Delivery fee", formatInr(shopping.summary.deliveryFee))
                SummaryLine("Handling / platform fee", formatInr(shopping.summary.handlingFee))
                SummaryLine("Item discount", "-${formatInr(shopping.summary.discount)}", valueColor = colors.statusSuccess)
                SummaryLine("Coupon savings", "-${formatInr(shopping.summary.couponSavings)}", valueColor = colors.statusSuccess)
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = colors.borderSubtle)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Final payable amount",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary
                    )
                    Text(
                        text = formatInr(shopping.summary.finalPayableAmount),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.accentPrimary
                    )
                }

                if (shopping.comparisonOptions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.borderSubtle)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Comparison summary",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shopping.comparisonOptions.forEach { option ->
                            StoreComparisonCard(option = option)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.statusInfoContainer.copy(alpha = 0.55f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = colors.statusInfo,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = shopping.nextStepDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions
                OrbitPrimaryButton(
                    onClick = onApproveAndContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Approve and continue")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OrbitSecondaryButton(
                        onClick = { isEditingBasket = !isEditingBasket },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isEditingBasket) "Done editing" else "Edit basket")
                    }
                    OrbitSecondaryButton(
                        onClick = { onOpenApp(shopping.storePackageName) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Launch, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open app")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OrbitSecondaryButton(
                    onClick = onReject,
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = colors.statusError.copy(alpha = 0.45f),
                    contentColor = colors.statusError
                ) {
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: BasketItem,
    isEditing: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val colors = OrbitTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.6f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accentContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (item.imageHint) {
                    "coffee" -> Icons.Rounded.Coffee
                    "eggs" -> Icons.Rounded.Egg
                    "milk" -> Icons.Rounded.LocalCafe
                    else -> Icons.Rounded.ShoppingBasket
                },
                contentDescription = item.name,
                tint = colors.accentPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary
            )
            Text(
                text = "${item.brand} • ${item.unitLabel} • ${formatInr(item.unitPrice)} each",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            if (item.discount > 0.0) {
                Text(
                    text = "Discount: -${formatInr(item.discount * item.quantity)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.statusSuccess
                )
            }
        }
        if (isEditing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrement, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Remove, "Decrease quantity", tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary
                )
                IconButton(onClick = onIncrement, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Add, "Increase quantity", tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatInr(item.lineTotal),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                Text(
                    text = "Qty ${item.quantity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }
    }
}

@Composable
private fun StoreComparisonCard(option: StoreComparisonOption) {
    val colors = OrbitTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (option.isSelected) colors.accentContainer.copy(alpha = 0.55f)
                else colors.surfaceElevated.copy(alpha = 0.45f)
            )
            .border(
                width = 1.dp,
                color = if (option.isSelected) colors.accentPrimary.copy(alpha = 0.5f) else colors.borderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = if (option.isSelected) "Selected" else null,
            tint = if (option.isSelected) colors.accentPrimary else colors.textTertiary.copy(alpha = 0.35f),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = option.storeName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                Text(
                    text = "${formatInr(option.basketTotal)} + ${formatInr(option.deliveryAndPlatformFees)} fees",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Savings: ${formatInr(option.savings)} • ${option.selectionReason}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun SummaryLine(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = OrbitTheme.colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OrbitTheme.colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = valueColor
        )
    }
}

private fun formatInr(amount: Double): String = "₹${amount.roundToInt()}"
