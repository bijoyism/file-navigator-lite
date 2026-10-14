package com.bijoyism.filenavigator.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencesDataStore by preferencesDataStore(name = "file_navigator_preferences")

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.preferencesDataStore

    val rootTreeUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ROOT_TREE_URI]
    }

    suspend fun saveRootTreeUri(uri: String) {
        dataStore.edit { preferences ->
            preferences[ROOT_TREE_URI] = uri
        }
    }

    private companion object {
        val ROOT_TREE_URI = stringPreferencesKey("root_tree_uri")
    }
}
