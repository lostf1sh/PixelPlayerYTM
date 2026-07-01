package com.lostf1sh.pixelplayeross.data.network.youtube

import android.content.Context
import androidx.core.content.edit
import com.lostf1sh.pixelplayeross.di.YouTubeHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Transport for YouTube's private `youtubei/v1` API. Every call is a POST whose body
 * carries a `context` block describing the [InnerTubeClientId] the request impersonates;
 * endpoint-specific fields are appended by the caller's [payload] lambda.
 *
 * Responses are returned as a parsed [JsonObject] tree — InnerTube's renderer soup has
 * no stable schema worth modelling, so the parser layer walks the tree defensively.
 *
 * Auth (OAuth Bearer / cookie SAPISIDHASH) is attached by the interceptor on the
 * injected [YouTubeHttp] client, not here.
 */
@Singleton
class InnerTubeService @Inject constructor(
    @YouTubeHttp private val httpClient: OkHttpClient,
    private val json: Json,
    private val visitorStore: YtVisitorStore,
) {

    class YtApiException(val httpCode: Int, bodySnippet: String) :
        IOException("InnerTube HTTP $httpCode: $bodySnippet")

    suspend fun call(
        endpoint: String,
        client: InnerTubeClientId = InnerTubeClientId.WEB_REMIX,
        payload: JsonObjectBuilder.() -> Unit = {},
    ): JsonObject = withContext(Dispatchers.IO) {
        val visitorId = visitorStore.current
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", client.clientName)
                    put("clientVersion", client.version)
                    put("hl", "en")
                    put("gl", "US")
                    client.androidSdkVersion?.let { put("androidSdkVersion", it) }
                    visitorId?.let { put("visitorData", it) }
                }
                putJsonObject("user") { put("lockedSafetyMode", false) }
            }
            payload()
        }

        // The `key` query param authenticates the *app*; when the interceptor attaches a
        // user Bearer token it removes this param again — sending both makes Google pick
        // the key and silently drop the user identity (empty library, anonymous feed).
        val url = "$API_BASE$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("key", client.apiKey)
            .addQueryParameter("prettyPrint", "false")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MEDIA_TYPE))
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.headerId)
            .header("X-YouTube-Client-Version", client.version)
            .header("Origin", ORIGIN)
            .apply {
                client.referer?.let { header("Referer", it) }
                visitorId?.let { header("X-Goog-Visitor-Id", it) }
            }
            .build()

        httpClient.newCall(request).awaitResponse().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Timber.tag(TAG).w("$endpoint failed: HTTP ${response.code}")
                throw YtApiException(response.code, text.take(400))
            }
            val root = json.parseToJsonElement(text).jsonObject
            if (visitorId == null) captureVisitorId(root)
            root
        }
    }

    /** Keep the anonymous visitor id stable so recommendations don't reset every launch. */
    private fun captureVisitorId(root: JsonObject) {
        val fresh = root["responseContext"]?.jsonObject
            ?.get("visitorData")?.jsonPrimitive?.contentOrNull
        if (!fresh.isNullOrBlank()) visitorStore.current = fresh
    }

    private companion object {
        const val TAG = "InnerTubeService"
        const val API_BASE = "https://music.youtube.com/youtubei/v1/"
        const val ORIGIN = "https://music.youtube.com"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Persists InnerTube's anonymous `visitorData` token (not a secret — it just keeps the
 * recommendation profile stable across restarts). Plain SharedPreferences with an
 * in-memory copy so the request hot path never blocks.
 */
@Singleton
class YtVisitorStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("yt_visitor", Context.MODE_PRIVATE)

    @Volatile
    private var cached: String? = prefs.getString(KEY, null)

    var current: String?
        get() = cached
        set(value) {
            if (value.isNullOrBlank() || value == cached) return
            cached = value
            prefs.edit { putString(KEY, value) }
        }

    fun reset() {
        cached = null
        prefs.edit { remove(KEY) }
    }

    private companion object {
        const val KEY = "visitor_data"
    }
}

/** Suspends until the OkHttp call completes; cancelling the coroutine cancels the call. */
private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
