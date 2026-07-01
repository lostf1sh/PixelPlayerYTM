package com.lostf1sh.pixelplayerytm.data.innertube

import com.lostf1sh.pixelplayerytm.data.innertube.model.AccountMenuBody
import com.lostf1sh.pixelplayerytm.data.innertube.model.BrowseBody
import com.lostf1sh.pixelplayerytm.data.innertube.model.Context
import com.lostf1sh.pixelplayerytm.data.innertube.model.GetSearchSuggestionsBody
import com.lostf1sh.pixelplayerytm.data.innertube.model.LikeBody
import com.lostf1sh.pixelplayerytm.data.innertube.model.NextBody
import com.lostf1sh.pixelplayerytm.data.innertube.model.PlayerBody
import com.lostf1sh.pixelplayerytm.data.innertube.model.SearchBody
import com.lostf1sh.pixelplayerytm.data.innertube.model.YouTubeClient
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.AccountMenuResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.BrowseResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.GetSearchSuggestionsResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.NextResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.PlayerResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.SearchResponse
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.VisitorIdResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class InnerTubeException(message: String, val code: Int = -1) : IOException(message)

/**
 * Low-level InnerTube endpoint client for music.youtube.com.
 * Auth (cookies / OAuth bearer) is applied by an OkHttp interceptor.
 */
@Singleton
class InnerTube @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val visitorDataStore: VisitorDataStore,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val hl: String get() = Locale.getDefault().language.ifBlank { "en" }
    private val gl: String get() = Locale.getDefault().country.ifBlank { "US" }

    @Volatile
    private var cachedVisitorData: String? = null
    private val visitorDataMutex = Mutex()

    suspend fun visitorData(): String? {
        cachedVisitorData?.let { return it }
        return visitorDataMutex.withLock {
            cachedVisitorData
                ?: visitorDataStore.get()?.also { cachedVisitorData = it }
                ?: fetchVisitorData()?.also {
                    cachedVisitorData = it
                    visitorDataStore.set(it)
                }
        }
    }

    private suspend fun fetchVisitorData(): String? = runCatching {
        postRaw<VisitorIdResponse>(
            endpoint = "visitor_id",
            client = YouTubeClient.WEB_REMIX,
            body = json.encodeToString(
                AccountMenuBody.serializer(),
                AccountMenuBody(context = context(YouTubeClient.WEB_REMIX, visitorData = null)),
            ),
        ).responseContext?.visitorData
    }.getOrNull()

    private suspend fun context(
        client: YouTubeClient,
        visitorData: String? = null,
    ): Context = client.toContext(visitorData, hl, gl)

    // ---------- Endpoints ----------

    suspend fun search(query: String, params: String? = null): SearchResponse {
        val client = YouTubeClient.WEB_REMIX
        return post(
            "search",
            client,
            SearchBody(context(client, visitorData()), query, params),
        )
    }

    suspend fun searchContinuation(continuation: String): SearchResponse {
        val client = YouTubeClient.WEB_REMIX
        return post(
            "search",
            client,
            SearchBody(context(client, visitorData()), query = "", params = null),
            extraQuery = mapOf("ctoken" to continuation, "continuation" to continuation, "type" to "next"),
        )
    }

    suspend fun searchSuggestions(input: String): GetSearchSuggestionsResponse {
        val client = YouTubeClient.WEB_REMIX
        return post(
            "music/get_search_suggestions",
            client,
            GetSearchSuggestionsBody(context(client, visitorData()), input),
        )
    }

    suspend fun browse(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): BrowseResponse {
        val client = YouTubeClient.WEB_REMIX
        return post(
            "browse",
            client,
            BrowseBody(context(client, visitorData()), browseId, params, continuation),
            extraQuery = continuation?.let {
                mapOf("ctoken" to it, "continuation" to it, "type" to "next")
            } ?: emptyMap(),
        )
    }

    suspend fun next(
        videoId: String? = null,
        playlistId: String? = null,
        index: Int? = null,
        params: String? = null,
        continuation: String? = null,
    ): NextResponse {
        val client = YouTubeClient.WEB_REMIX
        return post(
            "next",
            client,
            NextBody(
                context = context(client, visitorData()),
                videoId = videoId,
                playlistId = playlistId,
                index = index,
                params = params,
                continuation = continuation,
            ),
        )
    }

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        client: YouTubeClient = YouTubeClient.ANDROID_MUSIC,
        signatureTimestamp: Int? = null,
    ): PlayerResponse = post(
        "player",
        client,
        PlayerBody(
            context = context(client, visitorData()),
            videoId = videoId,
            playlistId = playlistId,
            playbackContext = signatureTimestamp?.let {
                PlayerBody.PlaybackContext(
                    PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp = it),
                )
            },
        ),
    )

    /** action: "like/like", "like/dislike" or "like/removelike" */
    suspend fun setLike(videoId: String, action: String): Boolean {
        val client = YouTubeClient.WEB_REMIX
        val body = json.encodeToString(
            LikeBody.serializer(),
            LikeBody(context(client, visitorData()), LikeBody.Target(videoId = videoId)),
        )
        return executeRaw(action, client, body).use { it.isSuccessful }
    }

    suspend fun accountMenu(): AccountMenuResponse {
        val client = YouTubeClient.WEB_REMIX
        return post(
            "account/account_menu",
            client,
            AccountMenuBody(context(client, visitorData())),
        )
    }

    // ---------- HTTP plumbing ----------

    private suspend inline fun <reified B, reified R> post(
        endpoint: String,
        client: YouTubeClient,
        body: B,
        extraQuery: Map<String, String> = emptyMap(),
    ): R {
        val bodyString = json.encodeToString(
            kotlinx.serialization.serializer<B>(),
            body,
        )
        return postRaw(endpoint, client, bodyString, extraQuery)
    }

    private suspend inline fun <reified R> postRaw(
        endpoint: String,
        client: YouTubeClient,
        body: String,
        extraQuery: Map<String, String> = emptyMap(),
    ): R = executeRaw(endpoint, client, body, extraQuery).use { response ->
        val text = withContext(Dispatchers.IO) { response.body.string() }
        json.decodeFromString(kotlinx.serialization.serializer<R>(), text)
    }

    private suspend fun executeRaw(
        endpoint: String,
        client: YouTubeClient,
        body: String,
        extraQuery: Map<String, String> = emptyMap(),
    ): okhttp3.Response = withContext(Dispatchers.IO) {
        val url = "https://music.youtube.com/youtubei/v1/$endpoint".toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", client.apiKey)
            .addQueryParameter("prettyPrint", "false")
            .apply { extraQuery.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(jsonMediaType))
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.clientId.toString())
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("Origin", YouTubeClient.ORIGIN_MUSIC)
            .header("Referer", client.referer ?: YouTubeClient.ORIGIN_MUSIC)
            .apply {
                cachedVisitorData?.let { header("X-Goog-Visitor-Id", it) }
            }
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val message = response.body.string().take(500)
            response.close()
            throw InnerTubeException(
                "InnerTube $endpoint failed: HTTP ${response.code} $message",
                response.code,
            )
        }
        response
    }
}
