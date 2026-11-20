package com.orbit.assistant.data.preferences

enum class AutonomyLevel(val label: String, val description: String) {
    SUPERVISED("Supervised", "Ask before every major app action and purchase"),
    BALANCED("Balanced", "Execute routine navigation automatically; confirm sensitive steps"),
    AUTONOMOUS("Autonomous", "Run multi-step workflows smoothly; still confirm payments")
}

enum class ExecutionBehavior(val label: String) {
    STEP_BY_STEP("Show live timeline"),
    COMPACT("Compact progress updates")
}

enum class LlmProvider(val label: String) {
    DEMO("Demo Mode (Simulated)"),
    GEMINI("Google Gemini"),
    OPENAI_COMPATIBLE("OpenAI / Compatible")
}

data class OrbitSettings(
    val isDarkMode: Boolean = true,
    val autonomyLevel: AutonomyLevel = AutonomyLevel.BALANCED,
    val confirmSensitiveActions: Boolean = true,
    val confirmBeforePurchases: Boolean = true,
    val confirmBeforeSendingMessages: Boolean = true,
    val confirmBeforeDestructiveActions: Boolean = true,
    val preferredShoppingApps: Set<String> = setOf("Blinkit", "Zepto", "Swiggy Instamart"),
    val preferredBrands: String = "Amul, Akshayakalpa, Blue Tokai, Harvest Gold",
    val preferredCategories: String = "Dairy, Fresh Bakery, Coffee, Pantry",
    val preferredLanguage: String = "English",
    val defaultExecutionBehavior: ExecutionBehavior = ExecutionBehavior.STEP_BY_STEP,
    val retainScreenshotsLocally: Boolean = false,
    val llmProvider: LlmProvider = LlmProvider.DEMO,
    val llmApiKey: String = "",
    val llmModel: String = "gemini-2.5-flash",
    val llmBaseUrl: String = "https://api.openai.com/v1"
)
