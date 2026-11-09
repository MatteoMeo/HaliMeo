package it.halimeo.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionData by preferencesDataStore("halimeo_session")
class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    private val userKey = stringPreferencesKey("user_id")
    private val nameKey = stringPreferencesKey("display_name")
    private val activatedKey = booleanPreferencesKey("account_activated")
    suspend fun save(token: String, userId: String, name: String, activated: Boolean = true) {
        context.sessionData.edit {
            it[tokenKey]=token
            it[userKey]=userId
            it[nameKey]=name
            it[activatedKey]=activated
        }
    }
    suspend fun token() = context.sessionData.data.first()[tokenKey]
    suspend fun name() = context.sessionData.data.first()[nameKey].orEmpty()
    suspend fun userId() = context.sessionData.data.first()[userKey].orEmpty()
    suspend fun activated() = context.sessionData.data.first()[activatedKey] ?: true
    suspend fun clear() { context.sessionData.edit { it.clear() } }
}
