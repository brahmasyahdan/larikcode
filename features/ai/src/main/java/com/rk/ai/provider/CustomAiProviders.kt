package com.rk.ai.provider

import com.rk.ai.icons.Robot
import com.rk.ai.settings.AiSettings
import com.rk.settings.CachedPreference
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A user-defined OpenAI-compatible endpoint, e.g. a self-hosted gateway or a provider without a built-in. */
data class CustomProviderSpec(val id: String, val name: String, val apiRoot: String, val models: List<String>)

/** Custom providers may run without a key (local servers), so the key is optional. */
class CustomAiProvider(val spec: CustomProviderSpec, endpoint: OpenAiEndpoint.Parts) :
    OpenAiCompatibleProvider(
        id = spec.id,
        displayName = spec.name,
        icon = Robot,
        baseUrl = endpoint.baseUrl,
        requestPath = endpoint.requestPath,
        models = spec.models.map { AiModel(id = it, providerId = spec.id) },
        requiresApiKey = false,
    )

object CustomAiProviders {
    private const val ID_PREFIX = "custom_"

    private var stored by CachedPreference("ai_custom_providers", "[]")

    val specs: List<CustomProviderSpec>
        get() = decode(stored)

    fun newId(): String = ID_PREFIX + UUID.randomUUID().toString().take(8)

    fun isCustom(providerId: String): Boolean = providerId.startsWith(ID_PREFIX)

    /** Registers every saved provider; called once alongside the built-ins. */
    internal fun install() {
        specs.forEach { spec -> spec.toProvider()?.let(AiProviderRegistry::registerProvider) }
    }

    /** Adds [spec], or replaces the saved provider with the same id. */
    fun save(spec: CustomProviderSpec) {
        val provider = requireNotNull(spec.toProvider()) { "Invalid API root: ${spec.apiRoot}" }
        val current = specs
        val updated =
            if (current.any { it.id == spec.id }) current.map { if (it.id == spec.id) spec else it } else current + spec
        stored = encode(updated)
        AiProviderRegistry.registerProvider(provider)
        AiProviderRuntime.invalidate()
    }

    fun remove(id: String) {
        stored = encode(specs.filterNot { it.id == id })
        AiProviderRegistry.unregisterProvider(id)
        AiSettings.setApiKey(id, "")
        FetchedModels.clear(id)
        AiProviderRuntime.invalidate()
    }

    private fun CustomProviderSpec.toProvider(): CustomAiProvider? =
        OpenAiEndpoint.parse(apiRoot)?.let { CustomAiProvider(this, it) }

    private fun decode(raw: String): List<CustomProviderSpec> = runCatching {
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            CustomProviderSpec(
                id = id,
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: id,
                apiRoot = obj["apiRoot"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                models = (obj["models"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
            )
        }
    }
        .getOrDefault(emptyList())

    private fun encode(specs: List<CustomProviderSpec>): String = buildJsonArray {
        specs.forEach { spec ->
            add(
                buildJsonObject {
                    put("id", JsonPrimitive(spec.id))
                    put("name", JsonPrimitive(spec.name))
                    put("apiRoot", JsonPrimitive(spec.apiRoot))
                    put("models", JsonArray(spec.models.map(::JsonPrimitive)))
                }
            )
        }
    }
        .toString()
}
