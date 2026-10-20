package com.rk.ai.provider

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.ai.icons.AiBrain
import com.rk.ai.icons.AnthropicIcon
import com.rk.ai.icons.DeepSeekIcon
import com.rk.ai.icons.OpenAiIcon
import com.rk.ai.icons.Robot
import com.rk.ai.icons.SparklesTiny

object BuiltinProviders {
    const val OPENAI_ID = "openai"
    const val ANTHROPIC_ID = "anthropic"
    const val OPENROUTER_ID = "openrouter"
    const val GEMINI_ID = "gemini"
    const val DEEPSEEK_ID = "deepseek"
    const val GROQ_ID = "groq"
    const val MISTRAL_ID = "mistral"
    const val XAI_ID = "xai"
    const val OLLAMA_ID = "ollama"

    val openAi: AiProvider =
        OpenAiCompatibleProvider(
            id = OPENAI_ID,
            displayName = "OpenAI",
            icon = OpenAiIcon,
            baseUrl = "https://api.openai.com",
            requestPath = "v1/chat/completions",
            models =
                listOf(
                    AiModel(id = "gpt-4o", providerId = OPENAI_ID),
                    AiModel(id = "gpt-4o-mini", providerId = OPENAI_ID),
                    AiModel(id = "gpt-4.1", providerId = OPENAI_ID),
                    AiModel(id = "gpt-4.1-mini", providerId = OPENAI_ID),
                ),
        )

    val anthropic: AiProvider =
        AnthropicProvider(
            id = ANTHROPIC_ID,
            displayName = "Anthropic",
            icon = AnthropicIcon,
            models =
                listOf(
                    anthropicModel(AnthropicModels.Sonnet_4_6, "Claude Sonnet 4.6"),
                    anthropicModel(AnthropicModels.Sonnet_4_5, "Claude Sonnet 4.5"),
                    anthropicModel(AnthropicModels.Opus_4_7, "Claude Opus 4.7"),
                    anthropicModel(AnthropicModels.Opus_4_6, "Claude Opus 4.6"),
                    anthropicModel(AnthropicModels.Opus_4_5, "Claude Opus 4.5"),
                    anthropicModel(AnthropicModels.Haiku_4_5, "Claude Haiku 4.5"),
                ),
        )

    /** One key for hundreds of models; use "fetch models" in settings to load the full catalog. */
    val openRouter: AiProvider =
        compatible(
            id = OPENROUTER_ID,
            displayName = "OpenRouter",
            icon = Robot,
            apiRoot = "https://openrouter.ai/api/v1",
            "openrouter/auto",
            "anthropic/claude-sonnet-4.5",
            "openai/gpt-4.1-mini",
            "google/gemini-2.5-flash",
            "deepseek/deepseek-chat-v3.1",
            "qwen/qwen3-coder",
            "x-ai/grok-code-fast-1",
        )

    val gemini: AiProvider =
        compatible(
            id = GEMINI_ID,
            displayName = "Google Gemini",
            icon = SparklesTiny,
            apiRoot = "https://generativelanguage.googleapis.com/v1beta/openai",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.5-flash-lite",
        )

    val deepSeek: AiProvider =
        compatible(
            id = DEEPSEEK_ID,
            displayName = "DeepSeek",
            icon = DeepSeekIcon,
            apiRoot = "https://api.deepseek.com/v1",
            "deepseek-chat",
            "deepseek-reasoner",
        )

    val groq: AiProvider =
        compatible(
            id = GROQ_ID,
            displayName = "Groq",
            icon = AiBrain,
            apiRoot = "https://api.groq.com/openai/v1",
            "llama-3.3-70b-versatile",
            "openai/gpt-oss-120b",
            "moonshotai/kimi-k2-instruct",
            "qwen/qwen3-32b",
        )

    val mistral: AiProvider =
        compatible(
            id = MISTRAL_ID,
            displayName = "Mistral AI",
            icon = AiBrain,
            apiRoot = "https://api.mistral.ai/v1",
            "mistral-large-latest",
            "mistral-medium-latest",
            "codestral-latest",
        )

    val xai: AiProvider =
        compatible(
            id = XAI_ID,
            displayName = "xAI (Grok)",
            icon = AiBrain,
            apiRoot = "https://api.x.ai/v1",
            "grok-4",
            "grok-code-fast-1",
            "grok-3-mini",
        )

    /** A local Ollama server; models come from "fetch models" since they depend on what was pulled. */
    val ollama: AiProvider =
        compatible(
            id = OLLAMA_ID,
            displayName = "Ollama (local)",
            icon = Robot,
            apiRoot = "http://localhost:11434/v1",
            requiresApiKey = false,
        )

    fun all(): List<AiProvider> = listOf(openRouter, openAi, anthropic, gemini, deepSeek, groq, mistral, xai, ollama)

    fun default(): AiProvider = anthropic

    private fun anthropicModel(model: LLModel, displayName: String): AiModel =
        AiModel(
            id = model.id,
            providerId = ANTHROPIC_ID,
            displayName = displayName,
            capabilities = model.capabilities.orEmpty(),
            contextWindowTokens = model.contextLength?.toInt(),
            llmModel = model,
        )

    private fun compatible(
        id: String,
        displayName: String,
        icon: ImageVector,
        apiRoot: String,
        vararg modelIds: String,
        requiresApiKey: Boolean = true,
    ): AiProvider {
        val endpoint = checkNotNull(OpenAiEndpoint.parse(apiRoot)) { "Invalid API root for $id: $apiRoot" }
        return OpenAiCompatibleProvider(
            id = id,
            displayName = displayName,
            icon = icon,
            baseUrl = endpoint.baseUrl,
            requestPath = endpoint.requestPath,
            models = modelIds.map { AiModel(id = it, providerId = id) },
            requiresApiKey = requiresApiKey,
        )
    }
}

/** Speaks the OpenAI chat-completions protocol; pass [executorFactory] to swap the client. */
open class OpenAiCompatibleProvider(
    override val id: String,
    override val displayName: String,
    override val icon: ImageVector,
    override val baseUrl: String,
    override val requestPath: String = "v1/chat/completions",
    override val models: List<AiModel> = emptyList(),
    override val requiresApiKey: Boolean = true,
    private val executorFactory: ((AiProviderConfig) -> PromptExecutor)? = null,
) : AiProvider {
    override val llmProvider: LLMProvider = LLMProvider.OpenAI

    /** The URL a user would type for this provider, e.g. `https://openrouter.ai/api/v1`. */
    val apiRoot: String
        get() = OpenAiEndpoint.apiRoot(baseUrl, requestPath)

    override fun createExecutor(config: AiProviderConfig): PromptExecutor =
        executorFactory?.invoke(config) ?: openAiExecutor(config)

    private fun openAiExecutor(config: AiProviderConfig): PromptExecutor =
        MultiLLMPromptExecutor(
            OpenAILLMClient(
                apiKey = config.apiKey,
                settings =
                    OpenAIClientSettings(
                        baseUrl = config.baseUrl,
                        chatCompletionsPath = config.requestPath,
                    ),
                httpClientFactory = KtorKoogHttpClient.Factory(),
            )
        )
}
