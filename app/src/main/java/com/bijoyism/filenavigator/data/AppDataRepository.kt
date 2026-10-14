package com.bijoyism.filenavigator.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bijoyism.filenavigator.domain.ConflictPolicy
import com.bijoyism.filenavigator.domain.OrganizationRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.appDataStore by preferencesDataStore(name = "file_navigator_data")

data class OperationRecord(
    val id: String = UUID.randomUUID().toString(),
    val operation: String,
    val name: String,
    val sourceUri: String = "",
    val destinationUri: String = "",
    val sourceParentUri: String = "",
    val destinationParentUri: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val success: Boolean,
    val message: String,
    val undoAction: String = "",
)

data class FavoriteRecord(
    val uri: String,
    val name: String,
    val mimeType: String?,
    val isDirectory: Boolean,
    val addedAtMillis: Long = System.currentTimeMillis(),
)

data class AppSettings(
    val automaticScanning: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val quietMode: Boolean = false,
    val darkTheme: Boolean = false,
)

class AppDataRepository(context: Context) {
    private val store = context.applicationContext.appDataStore

    val rules: Flow<List<OrganizationRule>> = store.data.map { it[RULES_JSON].decodeRules() }
    val history: Flow<List<OperationRecord>> = store.data.map { it[HISTORY_JSON].decodeHistory() }
    val favorites: Flow<List<FavoriteRecord>> = store.data.map { it[FAVORITES_JSON].decodeFavorites() }
    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            automaticScanning = prefs[AUTO_SCAN] ?: false,
            notificationsEnabled = prefs[NOTIFICATIONS] ?: false,
            quietMode = prefs[QUIET_MODE] ?: false,
            darkTheme = prefs[DARK_THEME] ?: false,
        )
    }

    suspend fun saveRule(rule: OrganizationRule) {
        store.edit { prefs ->
            val current = prefs[RULES_JSON].decodeRules().toMutableList()
            val index = current.indexOfFirst { it.id == rule.id }
            if (index >= 0) current[index] = rule else current.add(0, rule)
            prefs[RULES_JSON] = current.encodeRules()
        }
    }

    suspend fun deleteRule(id: String) {
        store.edit { prefs -> prefs[RULES_JSON] = prefs[RULES_JSON].decodeRules().filterNot { it.id == id }.encodeRules() }
    }

    suspend fun updateRuleResult(id: String, summary: String) {
        store.edit { prefs ->
            prefs[RULES_JSON] = prefs[RULES_JSON].decodeRules().map { rule ->
                if (rule.id == id) rule.copy(lastRunMillis = System.currentTimeMillis(), lastRunSummary = summary) else rule
            }.encodeRules()
        }
    }

    suspend fun addHistory(record: OperationRecord) {
        store.edit { prefs ->
            val entries = prefs[HISTORY_JSON].decodeHistory().toMutableList()
            entries.add(0, record)
            prefs[HISTORY_JSON] = entries.take(MAX_HISTORY).encodeHistory()
        }
    }

    suspend fun clearHistory() {
        store.edit { it[HISTORY_JSON] = "[]" }
    }

    suspend fun markUndoUsed(id: String) {
        store.edit { prefs ->
            prefs[HISTORY_JSON] = prefs[HISTORY_JSON].decodeHistory().map { record ->
                if (record.id == id) record.copy(undoAction = "") else record
            }.encodeHistory()
        }
    }

    suspend fun saveFavorite(favorite: FavoriteRecord) {
        store.edit { prefs ->
            val favorites = prefs[FAVORITES_JSON].decodeFavorites().filterNot { it.uri == favorite.uri }.toMutableList()
            favorites.add(0, favorite)
            prefs[FAVORITES_JSON] = favorites.take(MAX_FAVORITES).encodeFavorites()
        }
    }

    suspend fun removeFavorite(uri: String) {
        store.edit { prefs -> prefs[FAVORITES_JSON] = prefs[FAVORITES_JSON].decodeFavorites().filterNot { it.uri == uri }.encodeFavorites() }
    }

    suspend fun updateSettings(settings: AppSettings) {
        store.edit { prefs ->
            prefs[AUTO_SCAN] = settings.automaticScanning
            prefs[NOTIFICATIONS] = settings.notificationsEnabled
            prefs[QUIET_MODE] = settings.quietMode
            prefs[DARK_THEME] = settings.darkTheme
        }
    }

    suspend fun seenFileKeys(ruleId: String): Set<String> =
        store.data.first()[seenKey(ruleId)].orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    suspend fun saveSeenFileKeys(ruleId: String, keys: Set<String>) {
        store.edit { it[seenKey(ruleId)] = keys.takeLast(MAX_SEEN_KEYS).joinToString("\n") }
    }

    private fun seenKey(ruleId: String) = stringPreferencesKey("seen_files_$ruleId")

    private companion object {
        const val MAX_HISTORY = 200
        const val MAX_FAVORITES = 500
        const val MAX_SEEN_KEYS = 3_000
        val RULES_JSON = stringPreferencesKey("rules_json")
        val HISTORY_JSON = stringPreferencesKey("history_json")
        val FAVORITES_JSON = stringPreferencesKey("favorites_json")
        val AUTO_SCAN = booleanPreferencesKey("automatic_scanning")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val QUIET_MODE = booleanPreferencesKey("quiet_mode")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }
}


private fun String?.decodeRules(): List<OrganizationRule> = runCatching {
    val array = JSONArray(this ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        OrganizationRule(
            id = item.optString("id", UUID.randomUUID().toString()),
            name = item.optString("name"),
            sourceTreeUri = item.optString("sourceTreeUri"),
            destinationTreeUri = item.optString("destinationTreeUri"),
            extensions = item.optJSONArray("extensions").toStringSet(),
            mimeTypes = item.optJSONArray("mimeTypes").toStringSet(),
            filenameKeywords = item.optJSONArray("filenameKeywords").toStringSet(),
            minBytes = item.optNullableLong("minBytes"),
            maxBytes = item.optNullableLong("maxBytes"),
            enabled = item.optBoolean("enabled", true),
            moveFiles = item.optBoolean("moveFiles", true),
            conflictPolicy = runCatching { ConflictPolicy.valueOf(item.optString("conflictPolicy", "UNIQUE_NAME")) }
                .getOrDefault(ConflictPolicy.UNIQUE_NAME),
            lastRunMillis = item.optLong("lastRunMillis"),
            lastRunSummary = item.optString("lastRunSummary", "Not run yet"),
        )
    }
}.getOrDefault(emptyList())

private fun List<OrganizationRule>.encodeRules(): String = JSONArray().apply {
    this@encodeRules.forEach { rule ->
        put(JSONObject().apply {
            put("id", rule.id)
            put("name", rule.name)
            put("sourceTreeUri", rule.sourceTreeUri)
            put("destinationTreeUri", rule.destinationTreeUri)
            put("extensions", rule.extensions.toJsonArray())
            put("mimeTypes", rule.mimeTypes.toJsonArray())
            put("filenameKeywords", rule.filenameKeywords.toJsonArray())
            putNullable("minBytes", rule.minBytes)
            putNullable("maxBytes", rule.maxBytes)
            put("enabled", rule.enabled)
            put("moveFiles", rule.moveFiles)
            put("conflictPolicy", rule.conflictPolicy.name)
            put("lastRunMillis", rule.lastRunMillis)
            put("lastRunSummary", rule.lastRunSummary)
        })
    }
}.toString()

private fun String?.decodeHistory(): List<OperationRecord> = runCatching {
    val array = JSONArray(this ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        OperationRecord(
            id = item.optString("id", UUID.randomUUID().toString()),
            operation = item.optString("operation"),
            name = item.optString("name"),
            sourceUri = item.optString("sourceUri"),
            destinationUri = item.optString("destinationUri"),
            sourceParentUri = item.optString("sourceParentUri"),
            destinationParentUri = item.optString("destinationParentUri"),
            timestampMillis = item.optLong("timestampMillis"),
            success = item.optBoolean("success"),
            message = item.optString("message"),
            undoAction = item.optString("undoAction"),
        )
    }
}.getOrDefault(emptyList())

private fun List<OperationRecord>.encodeHistory(): String = JSONArray().apply {
    this@encodeHistory.forEach { item -> put(JSONObject().apply {
        put("id", item.id)
        put("operation", item.operation)
        put("name", item.name)
        put("sourceUri", item.sourceUri)
        put("destinationUri", item.destinationUri)
        put("sourceParentUri", item.sourceParentUri)
        put("destinationParentUri", item.destinationParentUri)
        put("timestampMillis", item.timestampMillis)
        put("success", item.success)
        put("message", item.message)
        put("undoAction", item.undoAction)
    }) }
}.toString()

private fun String?.decodeFavorites(): List<FavoriteRecord> = runCatching {
    val array = JSONArray(this ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        FavoriteRecord(
            uri = item.optString("uri"),
            name = item.optString("name"),
            mimeType = item.optString("mimeType").takeIf(String::isNotBlank),
            isDirectory = item.optBoolean("isDirectory"),
            addedAtMillis = item.optLong("addedAtMillis"),
        )
    }
}.getOrDefault(emptyList())

private fun List<FavoriteRecord>.encodeFavorites(): String = JSONArray().apply {
    this@encodeFavorites.forEach { item -> put(JSONObject().apply {
        put("uri", item.uri)
        put("name", item.name)
        put("mimeType", item.mimeType)
        put("isDirectory", item.isDirectory)
        put("addedAtMillis", item.addedAtMillis)
    }) }
}.toString()

private fun Set<String>.toJsonArray(): JSONArray = JSONArray().apply { this@toJsonArray.sorted().forEach(::put) }
private fun JSONArray?.toStringSet(): Set<String> = if (this == null) emptySet() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()
private fun JSONObject.optNullableLong(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
private fun JSONObject.putNullable(key: String, value: Long?) { if (value == null) put(key, JSONObject.NULL) else put(key, value) }
