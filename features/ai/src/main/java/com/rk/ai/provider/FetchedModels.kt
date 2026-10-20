package com.rk.ai.provider

import com.rk.settings.CachedPreference
import com.rk.utils.okHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/** Model ids loaded from a provider's `/models` endpoint, kept per provider id. */
object FetchedModels {
    private var stored by CachedPreference("ai_fetched_models", "{}")

    fun forProvider(providerId: String): List<AiModel> =
        decode(stored)[providerId].orEmpty().map { AiModel(id = it, providerId = providerId) }

    fun count(providerId: String): Int = decode(stored)[providerId].orEmpty().size

    fun set(providerId: String, modelIds: List<String>) {
        stored = encode(decode(stored) + (providerId to modelIds))
    }

    fun clear(providerId: String) {
        stored = encode(decode(stored) - providerId)
    }

    /** Loads the model list from [provider], saves it, and returns how many models were found. */
    suspend fun refresh(provider: OpenAiCompatibleProvider, apiKey: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request.Builder()
                        .url(OpenAiEndpoint.modelsUrl(provider.baseUrl, provider.requestPath))
                        .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
                        .get()
                        .build()
                val ids =
                    okHttpClient.newCall(request).execute().use { response ->
                        val body = response.body.string()
                        check(response.isSuccessful) { "HTTP ${response.code}: ${body.take(200)}" }
                        parseModelIds(body)
                    }
                check(ids.isNotEmpty()) { "No models returned" }
                set(provider.id, ids)
                ids.size
            }
        }

    /** Accepts the OpenAI shape `{"data":[{"id":…}]}`; Gemini prefixes ids with `models/`. */
    private fun parseModelIds(body: String): List<String> {
        val root = Json.parseToJsonElement(body)
        val entries = (root as? JsonObject)?.get("data") as? JsonArray ?: root as? JsonArray ?: JsonArray(emptyList())
        return entries
            .mapNotNull { (it as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull?.removePrefix("models/") }
            .distinct()
            .sorted()
    }

    private fun decode(raw: String): Map<String, List<String>> = runCatching {
        Json.parseToJsonElement(raw).jsonObject.mapValues { (_, ids) ->
            ids.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }
    }
        .getOrDefault(emptyMap())

    private fun encode(map: Map<String, List<String>>): String =
        JsonObject(map.mapValues { (_, ids) -> JsonArray(ids.map(::JsonPrimitive)) }).toString()
}
