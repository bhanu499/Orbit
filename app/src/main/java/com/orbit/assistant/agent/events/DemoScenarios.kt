package com.orbit.assistant.agent.events

import com.orbit.assistant.domain.models.AgentEvent
import com.orbit.assistant.domain.models.ApprovalHistoryEntry
import com.orbit.assistant.domain.models.ApprovalDecisionType
import com.orbit.assistant.domain.models.ApprovalRequest
import com.orbit.assistant.domain.models.BasketItem
import com.orbit.assistant.domain.models.BasketSummary
import com.orbit.assistant.domain.models.EventStatus
import com.orbit.assistant.domain.models.EventType
import com.orbit.assistant.domain.models.ShoppingApprovalData
import com.orbit.assistant.domain.models.StoreComparisonOption
import com.orbit.assistant.domain.models.Task
import com.orbit.assistant.domain.models.TaskResult
import com.orbit.assistant.domain.state.AgentState
import java.util.UUID
import kotlin.math.roundToInt

data class DemoStep(
    val eventType: EventType,
    val title: String,
    val description: String,
    val delayMs: Long = 650L,
    val metadata: Map<String, String> = emptyMap(),
    val pausesForIntervention: Boolean = false,
    val interventionReason: String? = null
)

data class DemoScenario(
    val id: String,
    val selectedApps: List<String>,
    val steps: List<DemoStep>,
    val approvalRequest: ApprovalRequest? = null,
    val finalResult: TaskResult
)

object DemoScenarios {

    fun matchScenario(prompt: String, taskId: String): DemoScenario {
        val lower = prompt.lowercase()
        return when {
            lower.contains("milk") && (lower.contains("compare") || (!lower.contains("eggs") && !lower.contains("bread") && !lower.contains("coffee"))) ->
                milkComparisonScenario(taskId)
            lower.contains("book") || lower.contains("reserve") || lower.contains("verify") || lower.contains("intervention") ->
                bookingWithInterventionScenario(taskId)
            lower.contains("organize") || lower.contains("day") || lower.contains("schedule") ->
                organizeDayScenario(taskId)
            else ->
                groceryBasketComparisonScenario(taskId)
        }
    }

    private fun groceryBasketComparisonScenario(taskId: String): DemoScenario {
        val items = listOf(
            BasketItem(
                itemId = "item_milk",
                name = "Taaza Toned Fresh Milk",
                brand = "Amul",
                quantity = 1,
                unitPrice = 56.0,
                discount = 2.0,
                imageHint = "milk",
                unitLabel = "1 L"
            ),
            BasketItem(
                itemId = "item_eggs",
                name = "Farm Fresh White Eggs",
                brand = "Eggoz",
                quantity = 1,
                unitPrice = 84.0,
                discount = 6.0,
                imageHint = "eggs",
                unitLabel = "6 pcs"
            ),
            BasketItem(
                itemId = "item_bread",
                name = "100% Whole Wheat Bread",
                brand = "Harvest Gold",
                quantity = 1,
                unitPrice = 55.0,
                discount = 5.0,
                imageHint = "bread",
                unitLabel = "400 g"
            ),
            BasketItem(
                itemId = "item_coffee",
                name = "Attikan Estate Ground Coffee",
                brand = "Blue Tokai",
                quantity = 1,
                unitPrice = 440.0,
                discount = 35.0,
                imageHint = "coffee",
                unitLabel = "250 g"
            )
        )

        val comparison = listOf(
            StoreComparisonOption(
                storeName = "Zepto",
                packageName = "com.zeptoconsumerapp",
                basketTotal = 587.0,
                deliveryAndPlatformFees = 19.0,
                savings = 68.0,
                isSelected = true,
                selectionReason = "Lowest total payable after ₹20 simulated pass savings & lower handling fee."
            ),
            StoreComparisonOption(
                storeName = "Blinkit",
                packageName = "com.grofers.customerapp",
                basketTotal = 602.0,
                deliveryAndPlatformFees = 29.0,
                savings = 34.0,
                isSelected = false,
                selectionReason = "₹25 higher overall due to delivery + handling fee."
            )
        )

        val summary = recalculateSummary(items, deliveryFee = 14.0, handlingFee = 5.0, couponSavings = 20.0)

        return DemoScenario(
            id = "grocery_basket_comparison",
            selectedApps = listOf("Blinkit", "Zepto"),
            steps = listOf(
                DemoStep(
                    eventType = EventType.Thinking,
                    title = "Understanding your request",
                    description = "Parsed 4 grocery items (milk, eggs, bread, coffee) across Blinkit and Zepto (Demo Mode).",
                    delayMs = 550L,
                    metadata = mapOf("mode" to "Demo Simulation", "items" to "4", "stores" to "Blinkit, Zepto")
                ),
                DemoStep(
                    eventType = EventType.AppLaunched("com.grofers.customerapp", "Blinkit"),
                    title = "Opening Blinkit",
                    description = "Simulated launch of Blinkit storefront session.",
                    delayMs = 600L,
                    metadata = mapOf("package" to "com.grofers.customerapp", "simulated" to "true")
                ),
                DemoStep(
                    eventType = EventType.SearchPerformed("milk, eggs, bread, coffee", "Blinkit"),
                    title = "Searching for milk & staples",
                    description = "Simulated catalog lookup for Amul Milk, Eggoz Eggs, Harvest Gold Bread, Blue Tokai Coffee.",
                    delayMs = 700L
                ),
                DemoStep(
                    eventType = EventType.ProductIdentified("Blinkit Basket", "₹631 est. total"),
                    title = "Comparing products on Blinkit",
                    description = "Recorded simulated item prices and ₹29 delivery/handling fees.",
                    delayMs = 600L
                ),
                DemoStep(
                    eventType = EventType.AppLaunched("com.zeptoconsumerapp", "Zepto"),
                    title = "Opening Zepto",
                    description = "Simulated launch of Zepto storefront session.",
                    delayMs = 600L,
                    metadata = mapOf("package" to "com.zeptoconsumerapp", "simulated" to "true")
                ),
                DemoStep(
                    eventType = EventType.SearchPerformed("milk, eggs, bread, coffee", "Zepto"),
                    title = "Searching for milk, eggs, bread & coffee",
                    description = "Matched equivalent brands and pack sizes in simulated Zepto catalog.",
                    delayMs = 700L
                ),
                DemoStep(
                    eventType = EventType.ItemAdded("4 grocery items", 4),
                    title = "Adding items to simulated cart",
                    description = "Staged 4 items in Zepto demo basket for total price verification.",
                    delayMs = 550L
                ),
                DemoStep(
                    eventType = EventType.Verification("Cart & Fee Verification"),
                    title = "Verifying cart & comparing prices",
                    description = "Zepto saves ₹45 vs Blinkit in this simulated comparison. Preparing approval summary.",
                    delayMs = 600L,
                    metadata = mapOf("winner" to "Zepto", "simulated_savings" to "₹45")
                )
            ),
            approvalRequest = ApprovalRequest(
                approvalId = "appr_${UUID.randomUUID().toString().take(8)}",
                title = "Review Zepto Grocery Basket",
                subtitle = "Demo simulation: confirm basket before Orbit opens checkout preparation",
                shoppingData = ShoppingApprovalData(
                    storeName = "Zepto",
                    storePackageName = "com.zeptoconsumerapp",
                    items = items,
                    summary = summary,
                    comparisonOptions = comparison,
                    nextStepDescription = "Authorizing continues to simulated checkout preparation only. Orbit never processes or completes payment."
                )
            ),
            finalResult = TaskResult(
                summaryMarkdown = """
                    **Zepto** is the cheapest simulated combination for your 4-item list.
                    - **Estimated Payable (Demo):** ₹${summary.finalPayableAmount.roundToInt()} (after ₹68 combined item & coupon savings)
                    - **Blinkit Comparison:** ₹631 total (₹45 higher)
                    - **Next step:** Basket approved for checkout handoff; payment remains entirely with you inside the store app.
                """.trimIndent(),
                highlights = listOf("Zepto selected", "4 items staged", "₹45 simulated savings vs Blinkit"),
                comparisonOptions = comparison,
                primaryActionLabel = "Open Zepto",
                primaryActionPackage = "com.zeptoconsumerapp"
            )
        )
    }

    private fun milkComparisonScenario(taskId: String): DemoScenario {
        val comparison = listOf(
            StoreComparisonOption(
                storeName = "Blinkit",
                packageName = "com.grofers.customerapp",
                basketTotal = 54.0,
                deliveryAndPlatformFees = 15.0,
                savings = 8.0,
                isSelected = true,
                selectionReason = "Lower simulated unit price for Amul Taaza 1L + smaller basket fee."
            ),
            StoreComparisonOption(
                storeName = "Zepto",
                packageName = "com.zeptoconsumerapp",
                basketTotal = 56.0,
                deliveryAndPlatformFees = 19.0,
                savings = 4.0,
                isSelected = false,
                selectionReason = "₹6 higher total after simulated handling fee."
            )
        )
        return DemoScenario(
            id = "milk_comparison",
            selectedApps = listOf("Blinkit", "Zepto"),
            steps = listOf(
                DemoStep(EventType.Thinking, "Understanding the request", "Comparing 1L toned milk options across Blinkit and Zepto (Demo Mode).", 500L),
                DemoStep(EventType.Thinking, "Identifying shopping apps", "Matched Blinkit and Zepto from supported shopping apps.", 450L),
                DemoStep(EventType.AppLaunched("com.grofers.customerapp", "Blinkit"), "Opening Blinkit", "Simulated Blinkit session started.", 550L),
                DemoStep(EventType.SearchPerformed("milk 1L", "Blinkit"), "Searching for milk", "Queried simulated Blinkit dairy catalog.", 600L),
                DemoStep(EventType.ProductIdentified("Amul Taaza 1L", "₹54"), "Identifying products", "Found Amul Taaza 1L (₹54) & Mother Dairy 1L (₹56).", 500L),
                DemoStep(EventType.Verification("Price Log"), "Recording price", "Recorded simulated Blinkit total: ₹69 incl. fees.", 450L),
                DemoStep(EventType.AppLaunched("com.zeptoconsumerapp", "Zepto"), "Opening Zepto", "Simulated Zepto session started.", 550L),
                DemoStep(EventType.SearchPerformed("milk 1L", "Zepto"), "Searching for milk", "Queried simulated Zepto dairy catalog.", 600L),
                DemoStep(EventType.ScreenObserved("Zepto dairy results"), "Comparing products", "Compared unit prices and delivery/platform fees.", 500L),
                DemoStep(EventType.Verification("Comparison Summary"), "Preparing comparison result", "Compiled side-by-side simulated comparison.", 500L)
            ),
            approvalRequest = null,
            finalResult = TaskResult(
                summaryMarkdown = """
                    **Simulated Milk Price Comparison** *(Demo Mode — illustrative prices only)*
                    - **Blinkit:** Amul Taaza 1L at **₹54** + ₹15 fees = **₹69**
                    - **Zepto:** Amul Taaza 1L at **₹56** + ₹19 fees = **₹75**
                    - **Best option:** Blinkit saves an estimated **₹6** on a single-item order.
                """.trimIndent(),
                highlights = listOf("Blinkit: ₹69 total", "Zepto: ₹75 total", "Demo simulation"),
                comparisonOptions = comparison,
                primaryActionLabel = "Open Blinkit",
                primaryActionPackage = "com.grofers.customerapp"
            )
        )
    }

    private fun bookingWithInterventionScenario(taskId: String): DemoScenario {
        return DemoScenario(
            id = "booking_intervention",
            selectedApps = listOf("Google Maps", "Calendar"),
            steps = listOf(
                DemoStep(EventType.Thinking, "Understanding your request", "Planning search & reservation check (Demo Mode).", 550L),
                DemoStep(EventType.AppLaunched("com.google.android.apps.maps", "Google Maps"), "Opening Maps", "Simulated lookup for nearby top-rated venues.", 600L),
                DemoStep(EventType.SearchPerformed("dinner table 8 PM", "Google Maps"), "Searching available options", "Checking simulated availability and ratings.", 650L),
                DemoStep(
                    eventType = EventType.UserInterventionRequired(
                        reason = "Account sign-in or party size confirmation needed",
                        suggestedAction = "Tap 'Continue' to resume simulation or 'Take over' to inspect manually."
                    ),
                    title = "Needs your attention: confirm reservation slot",
                    description = "Simulated booking flow paused for user confirmation (Table for 2 at 8:00 PM).",
                    delayMs = 550L,
                    pausesForIntervention = true,
                    interventionReason = "Confirm party size & time slot before continuing"
                ),
                DemoStep(EventType.Verification("Slot Confirmed"), "Verifying reservation details", "Simulated reservation details prepared for your review.", 600L)
            ),
            approvalRequest = null,
            finalResult = TaskResult(
                summaryMarkdown = """
                    **Reservation Prep Ready (Demo Mode)**
                    - **Time:** Tonight, 8:00 PM (Table for 2)
                    - **Status:** Ready for you to finalize directly in the booking app.
                """.trimIndent(),
                highlights = listOf("Intervention resolved", "Ready for final confirmation"),
                primaryActionLabel = "Open Maps",
                primaryActionPackage = "com.google.android.apps.maps"
            )
        )
    }

    private fun organizeDayScenario(taskId: String): DemoScenario {
        return DemoScenario(
            id = "organize_day",
            selectedApps = listOf("Calendar", "Tasks"),
            steps = listOf(
                DemoStep(EventType.Thinking, "Understanding your request", "Structuring focus blocks, errands, and breaks (Demo Mode).", 500L),
                DemoStep(EventType.AppLaunched("com.google.android.calendar", "Calendar"), "Checking Calendar", "Simulated scan of open time blocks today.", 600L),
                DemoStep(EventType.ScreenObserved("Afternoon open slots"), "Identifying free windows", "Found 2:00 PM – 4:00 PM and 5:30 PM – 6:30 PM open.", 550L),
                DemoStep(EventType.Verification("Schedule Plan"), "Preparing daily plan", "Balanced deep work, grocery run, and evening wind-down.", 550L)
            ),
            approvalRequest = null,
            finalResult = TaskResult(
                summaryMarkdown = """
                    **Suggested Day Plan (Demo Mode)**
                    - **2:00 PM – 4:00 PM:** Deep Focus Block (uninterrupted work)
                    - **4:15 PM – 4:45 PM:** Quick grocery & errand run
                    - **5:30 PM – 6:30 PM:** Wrap-up & inbox zero
                """.trimIndent(),
                highlights = listOf("2h focus block", "Errand window", "Balanced schedule"),
                primaryActionLabel = "Open Calendar",
                primaryActionPackage = "com.google.android.calendar"
            )
        )
    }

    fun recalculateSummary(
        items: List<BasketItem>,
        deliveryFee: Double,
        handlingFee: Double,
        couponSavings: Double
    ): BasketSummary {
        val itemCount = items.sumOf { it.quantity }
        val subtotal = items.sumOf { it.unitPrice * it.quantity }
        val itemDiscount = items.sumOf { it.discount * it.quantity }
        val finalPayable = (subtotal - itemDiscount - couponSavings + deliveryFee + handlingFee)
            .coerceAtLeast(0.0)
        return BasketSummary(
            itemCount = itemCount,
            subtotal = subtotal,
            deliveryFee = deliveryFee,
            handlingFee = handlingFee,
            discount = itemDiscount,
            couponSavings = couponSavings,
            finalPayableAmount = finalPayable
        )
    }

    fun seedSampleTasks(): List<Task> {
        val now = System.currentTimeMillis()
        val task1Id = "sample_task_1"
        val task2Id = "sample_task_2"
        val task3Id = "sample_task_3"

        return listOf(
            Task(
                taskId = task1Id,
                userPrompt = "Compare milk prices on Blinkit and Zepto.",
                createdAt = now - 3_600_000L * 4,
                updatedAt = now - 3_600_000L * 4 + 12_000L,
                status = AgentState.Completed,
                selectedApps = listOf("Blinkit", "Zepto"),
                events = listOf(
                    AgentEvent("e1", task1Id, now - 3_600_000L * 4, EventType.Thinking, "Understanding the request", "Parsed dairy comparison request.", EventStatus.COMPLETED),
                    AgentEvent("e2", task1Id, now - 3_600_000L * 4 + 3_000L, EventType.AppLaunched("com.grofers.customerapp", "Blinkit"), "Opening Blinkit", "Simulated Blinkit catalog check.", EventStatus.COMPLETED),
                    AgentEvent("e3", task1Id, now - 3_600_000L * 4 + 7_000L, EventType.AppLaunched("com.zeptoconsumerapp", "Zepto"), "Opening Zepto", "Simulated Zepto catalog check.", EventStatus.COMPLETED),
                    AgentEvent("e4", task1Id, now - 3_600_000L * 4 + 12_000L, EventType.Verification("Comparison"), "Comparison complete", "Blinkit was ₹6 cheaper in demo simulation.", EventStatus.COMPLETED)
                ),
                result = TaskResult(
                    summaryMarkdown = "**Blinkit** was ₹6 cheaper than Zepto for Amul Taaza 1L (Demo Mode).",
                    highlights = listOf("Blinkit: ₹69", "Zepto: ₹75")
                ),
                durationMs = 12_000L
            ),
            Task(
                taskId = task2Id,
                userPrompt = "Buy coffee beans and oat milk on Zepto.",
                createdAt = now - 86_400_000L,
                updatedAt = now - 86_400_000L + 18_500L,
                status = AgentState.Completed,
                selectedApps = listOf("Zepto"),
                events = listOf(
                    AgentEvent("e21", task2Id, now - 86_400_000L, EventType.Thinking, "Planning basket", "Identified 2 items for Zepto.", EventStatus.COMPLETED),
                    AgentEvent("e22", task2Id, now - 86_400_000L + 9_000L, EventType.ItemAdded("Coffee & Oat Milk", 2), "Staged basket", "Prepared 2 items for user approval.", EventStatus.COMPLETED)
                ),
                result = TaskResult(
                    summaryMarkdown = "Basket approved for checkout preparation on **Zepto**.",
                    highlights = listOf("Approved by user", "2 items")
                ),
                approvalHistory = listOf(
                    ApprovalHistoryEntry(
                        approvalId = "appr_sample_2",
                        title = "Review Zepto Basket",
                        decision = ApprovalDecisionType.APPROVED,
                        timestamp = now - 86_400_000L + 18_000L,
                        summary = "Approved 2 items (₹645 estimated payable)"
                    )
                ),
                durationMs = 18_500L
            ),
            Task(
                taskId = task3Id,
                userPrompt = "Book a cab to the airport at 6:00 AM.",
                createdAt = now - 86_400_000L * 2,
                updatedAt = now - 86_400_000L * 2 + 8_200L,
                status = AgentState.Failed,
                selectedApps = listOf("Uber"),
                events = listOf(
                    AgentEvent("e31", task3Id, now - 86_400_000L * 2, EventType.Thinking, "Understanding request", "Parsed airport ride booking.", EventStatus.COMPLETED),
                    AgentEvent("e32", task3Id, now - 86_400_000L * 2 + 8_200L, EventType.Error("APP_NOT_INSTALLED", true), "Ride app unavailable", "Target ride-hailing app was not detected on device.", EventStatus.FAILED)
                ),
                error = "Target app not installed on device.",
                durationMs = 8_200L
            )
        )
    }
}
