package com.orbit.assistant.domain.models

import com.orbit.assistant.domain.state.AgentState
import kotlinx.serialization.Serializable

@Serializable
enum class EventStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    NEEDS_ATTENTION
}

@Serializable
sealed class EventType {
    @Serializable
    data object Thinking : EventType()

    @Serializable
    data class AppLaunched(val packageName: String, val appName: String) : EventType()

    @Serializable
    data class ScreenObserved(val screenSummary: String) : EventType()

    @Serializable
    data class SearchPerformed(val query: String, val appName: String) : EventType()

    @Serializable
    data class ProductIdentified(val productName: String, val priceLabel: String) : EventType()

    @Serializable
    data class ItemAdded(val itemName: String, val quantity: Int) : EventType()

    @Serializable
    data class Verification(val checkName: String) : EventType()

    @Serializable
    data class Error(val errorCode: String, val recoverable: Boolean) : EventType()

    @Serializable
    data class UserInterventionRequired(val reason: String, val suggestedAction: String) : EventType()
}

@Serializable
data class AgentEvent(
    val eventId: String,
    val taskId: String,
    val timestamp: Long,
    val eventType: EventType,
    val title: String,
    val description: String,
    val status: EventStatus,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class BasketItem(
    val itemId: String,
    val name: String,
    val brand: String,
    val quantity: Int,
    val unitPrice: Double,
    val discount: Double = 0.0,
    val imageHint: String = "grocery",
    val unitLabel: String = "1 unit"
) {
    val lineTotal: Double
        get() = ((unitPrice - discount).coerceAtLeast(0.0)) * quantity
}

@Serializable
data class BasketSummary(
    val itemCount: Int,
    val subtotal: Double,
    val deliveryFee: Double,
    val handlingFee: Double,
    val discount: Double,
    val couponSavings: Double,
    val finalPayableAmount: Double
)

@Serializable
data class StoreComparisonOption(
    val storeName: String,
    val packageName: String,
    val basketTotal: Double,
    val deliveryAndPlatformFees: Double,
    val savings: Double,
    val isSelected: Boolean,
    val selectionReason: String
)

@Serializable
data class ShoppingApprovalData(
    val storeName: String,
    val storePackageName: String,
    val items: List<BasketItem>,
    val summary: BasketSummary,
    val comparisonOptions: List<StoreComparisonOption> = emptyList(),
    val nextStepDescription: String = "Authorize Orbit to proceed to checkout preparation in the selected app. Orbit will never submit payment."
)

@Serializable
data class ApprovalRequest(
    val approvalId: String,
    val title: String,
    val subtitle: String,
    val shoppingData: ShoppingApprovalData? = null
)

@Serializable
enum class ApprovalDecisionType {
    APPROVED,
    REJECTED,
    EDITED_AND_APPROVED
}

@Serializable
data class ApprovalDecision(
    val approvalId: String,
    val decision: ApprovalDecisionType,
    val note: String? = null
)

@Serializable
data class ApprovalHistoryEntry(
    val approvalId: String,
    val title: String,
    val decision: ApprovalDecisionType,
    val timestamp: Long,
    val summary: String
)

@Serializable
data class TaskResult(
    val summaryMarkdown: String,
    val highlights: List<String> = emptyList(),
    val comparisonOptions: List<StoreComparisonOption> = emptyList(),
    val primaryActionLabel: String? = null,
    val primaryActionPackage: String? = null
)

@Serializable
data class Task(
    val taskId: String,
    val userPrompt: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: AgentState,
    val selectedApps: List<String> = emptyList(),
    val events: List<AgentEvent> = emptyList(),
    val result: TaskResult? = null,
    val error: String? = null,
    val approvalRequired: ApprovalRequest? = null,
    val approvalHistory: List<ApprovalHistoryEntry> = emptyList(),
    val durationMs: Long = 0L
)

enum class MessageRole {
    USER,
    AGENT
}

data class ChatMessage(
    val messageId: String,
    val role: MessageRole,
    val text: String,
    val timestamp: Long,
    val linkedTaskId: String? = null,
    val isStreaming: Boolean = false
)
