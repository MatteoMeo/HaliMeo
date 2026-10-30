package it.halimeo.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionData by preferencesDataStore("halimeo_session")
class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("user_id")
    private val nameKey = stringPreferencesKey("display_name")
    suspend fun save(token: String, userId: String, name: String) {
        context.sessionData.edit { it[tokenKey]=token; it[userKey]=userId; it[nameKey]=name }
    }
    suspend fun token() = context.sessionData.data.first()[tokenKey]
    suspend fun name() = context.sessionData.data.first()[nameKey].orEmpty()
    suspend fun userId() = context.sessionData.data.first()[userKey].orEmpty()
    suspend fun clear() { context.sessionData.edit { it.clear() } }
}
