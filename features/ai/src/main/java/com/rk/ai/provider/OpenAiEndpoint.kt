package com.rk.ai.provider

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Splits a user-facing API root such as `https://openrouter.ai/api/v1` into the host-only base URL and the
 * chat-completions path koog expects, so any OpenAI-compatible service can be configured with one URL.
 */
object OpenAiEndpoint {
    private const val CHAT_COMPLETIONS = "chat/completions"

    data class Parts(val baseUrl: String, val requestPath: String)

    /** Null when [apiRoot] is not an absolute http(s) URL. */
    fun parse(apiRoot: String): Parts? {
        val url = apiRoot.trim().trimEnd('/').removeSuffix("/$CHAT_COMPLETIONS").toHttpUrlOrNull() ?: return null
        val defaultPort = if (url.isHttps) 443 else 80
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        val rootPath = url.encodedPath.trim('/')
        val requestPath = listOf(rootPath, CHAT_COMPLETIONS).filter { it.isNotEmpty() }.joinToString("/")
        return Parts(baseUrl = "${url.scheme}://${url.host}$port", requestPath = requestPath)
    }

    /** The API root the user would type, e.g. `https://openrouter.ai/api/v1`. */
    fun apiRoot(baseUrl: String, requestPath: String): String {
        val root = requestPath.trim('/').removeSuffix(CHAT_COMPLETIONS).trim('/')
        return if (root.isEmpty()) baseUrl.trimEnd('/') else "${baseUrl.trimEnd('/')}/$root"
    }

    /** The OpenAI-style model listing endpoint that sits next to chat completions. */
    fun modelsUrl(baseUrl: String, requestPath: String): String = apiRoot(baseUrl, requestPath) + "/models"
}
