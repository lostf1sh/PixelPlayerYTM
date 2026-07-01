package com.lostf1sh.pixelplayerytm.data.innertube

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.innerTubeDataStore by preferencesDataStore("innertube")

@Singleton
class VisitorDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("visitor_data")

    suspend fun get(): String? = context.innerTubeDataStore.data.first()[key]

    suspend fun set(visitorData: String) {
        context.innerTubeDataStore.edit { it[key] = visitorData }
    }
}
