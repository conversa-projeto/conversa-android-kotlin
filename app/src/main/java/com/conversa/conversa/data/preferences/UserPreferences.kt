package com.conversa.conversa.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {
    
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val USER_ID_KEY = intPreferencesKey("user_id")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val API_URL_KEY = stringPreferencesKey("api_url")
        private val SAVED_LOGIN_KEY = stringPreferencesKey("saved_login")
        private val SAVED_PASSWORD_KEY = stringPreferencesKey("saved_password")
    }
    
    val authToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }
    
    val userId: Flow<Int?> = context.dataStore.data.map { preferences ->
        preferences[USER_ID_KEY]
    }
    
    val userName: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[USER_NAME_KEY]
    }
    
    val apiUrl: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[API_URL_KEY]
    }
    
    val savedLogin: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SAVED_LOGIN_KEY]
    }
    
    val savedPassword: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SAVED_PASSWORD_KEY]
    }
    
    suspend fun saveUserData(token: String, userId: Int, userName: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
            preferences[USER_ID_KEY] = userId
            preferences[USER_NAME_KEY] = userName
        }
    }
    
    suspend fun saveApiUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[API_URL_KEY] = url
        }
    }
    
    suspend fun saveLoginCredentials(login: String, password: String) {
        context.dataStore.edit { preferences ->
            preferences[SAVED_LOGIN_KEY] = login
            preferences[SAVED_PASSWORD_KEY] = password
        }
    }
    
    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    suspend fun clearLoginCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(SAVED_LOGIN_KEY)
            preferences.remove(SAVED_PASSWORD_KEY)
        }
    }
    
    suspend fun getToken(): String? {
        var token: String? = null
        context.dataStore.data.map { preferences ->
            token = preferences[TOKEN_KEY]
        }
        return token
    }
}
