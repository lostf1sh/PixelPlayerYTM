package com.lostf1sh.pixelplayeross.data.innertube

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the InnerTube `visitorData` token. It is not a secret — it identifies an
 * anonymous "visitor" so YouTube returns stable, consistent recommendations across app
 * restarts instead of re-rolling a cold profile on every launch.
 *
 * Backed by plain SharedPreferences (with a volatile in-memory cache) so reads are cheap
 * and non-suspending on the request hot path.
 */
@Singleton
class VisitorDataStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cached: String? = prefs.getString(KEY_VISITOR_DATA, null)

    fun get(): String? = cached

    fun set(visitorData: String?) {
        if (visitorData.isNullOrBlank() || visitorData == cached) return
        cached = visitorData
        prefs.edit { putString(KEY_VISITOR_DATA, visitorData) }
    }

    fun clear() {
        cached = null
        prefs.edit { remove(KEY_VISITOR_DATA) }
    }

    /** Pull `responseContext.visitorData` out of a raw InnerTube response and store it. */
    fun extractAndStore(rawResponse: String) {
        val visitorData = runCatching {
            JSONObject(rawResponse)
                .optJSONObject("responseContext")
                ?.optString("visitorData")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
        set(visitorData)
    }

    private companion object {
        const val PREFS_NAME = "youtube_visitor_data"
        const val KEY_VISITOR_DATA = "visitor_data"
    }
}
