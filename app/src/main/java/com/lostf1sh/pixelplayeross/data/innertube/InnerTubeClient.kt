package com.lostf1sh.pixelplayeross.data.innertube

import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Low-level InnerTube transport. Wraps the shared (YouTube-scoped) OkHttpClient and
 * knows how to POST an endpoint body to `https://music.youtube.com/youtubei/v1/{endpoint}`
 * with the correct client context and headers.
 *
 * Callers pass endpoint-specific fields via [bodyBuilder]; this class injects the
 * `context` block (client identity + [locale] + visitorData) and returns the raw JSON
 * string for the parsers to decode. Keeping decode out of here means one place owns the
 * lenient [Json] and response models stay in the parser/api layer.
 */
class InnerTubeClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val visitorDataStore: VisitorDataStore,
    private val locale: InnerTubeLocale = InnerTubeLocale(),
) {
    data class InnerTubeLocale(val hl: String = "en", val gl: String = "US")

    private val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

    /**
     * POST [endpoint] (e.g. "search", "player", "browse", "next") with a body composed of
     * the shared context plus whatever [bodyBuilder] adds. Returns the raw response body.
     */
    suspend fun post(
        endpoint: String,
        client: YouTubeClient = YouTubeClient.WEB_REMIX,
        bodyBuilder: JsonObjectBuilder.() -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val visitorData = visitorDataStore.get()
        val body = buildJsonObject {
            put("context", buildContext(client, visitorData))
            bodyBuilder()
        }

        val url = buildString {
            append(BASE_URL)
            append(endpoint)
            append("?key=")
            append(client.apiKey)
            append("&prettyPrint=false")
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("User-Agent", client.userAgent)
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("Origin", "https://music.youtube.com")

        client.referer?.let { requestBuilder.header("Referer", it) }
        visitorData?.let { requestBuilder.header("X-Goog-Visitor-Id", it) }

        val response = okHttpClient.newCall(requestBuilder.build()).await()
        response.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw InnerTubeException(it.code, text.take(500))
            }
            // Opportunistically capture a fresh visitorData from the first successful
            // anonymous call so recommendations stay stable across sessions.
            if (visitorData == null) {
                visitorDataStore.extractAndStore(text)
            }
            text
        }
    }

    private fun buildContext(client: YouTubeClient, visitorData: String?): JsonObject = buildJsonObject {
        put("client", buildJsonObject {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", locale.hl)
            put("gl", locale.gl)
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
            visitorData?.let { put("visitorData", it) }
        })
        put("user", buildJsonObject {
            put("lockedSafetyMode", false)
        })
    }

    companion object {
        const val BASE_URL = "https://music.youtube.com/youtubei/v1/"
    }
}

class InnerTubeException(val code: Int, val bodySnippet: String) :
    IOException("InnerTube request failed: HTTP $code — $bodySnippet")

/** Bridges an OkHttp [Call] to a cancellable coroutine. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation {
        runCatching { cancel() }
    }
}
