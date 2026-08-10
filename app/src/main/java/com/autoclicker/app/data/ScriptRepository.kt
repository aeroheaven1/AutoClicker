package com.autoclicker.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scripts")

class ScriptRepository(private val context: Context) {

    companion object {
        private val SCRIPTS_KEY = stringPreferencesKey("saved_scripts")
    }

    val scriptsFlow: Flow<List<Script>> = context.dataStore.data.map { prefs ->
        val json = prefs[SCRIPTS_KEY] ?: "[]"
        try {
            Script.listFromJson(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveScript(script: Script) {
        context.dataStore.edit { prefs ->
            val current = prefs[SCRIPTS_KEY] ?: "[]"
            val scripts = try {
                Script.listFromJson(current).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            val index = scripts.indexOfFirst { it.id == script.id }
            val updated = script.copy(updatedAt = System.currentTimeMillis())
            if (index >= 0) {
                scripts[index] = updated
            } else {
                scripts.add(updated)
            }
            prefs[SCRIPTS_KEY] = Script.listToJson(scripts)
        }
    }

    suspend fun deleteScript(scriptId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[SCRIPTS_KEY] ?: "[]"
            val scripts = try {
                Script.listFromJson(current).toMutableList()
            } catch (e: Exception) {
                mutableListOf()
            }
            scripts.removeAll { it.id == scriptId }
            prefs[SCRIPTS_KEY] = Script.listToJson(scripts)
        }
    }

    suspend fun getAllScripts(): List<Script> {
        // snapshot
        var result: List<Script> = emptyList()
        context.dataStore.edit { prefs ->
            val json = prefs[SCRIPTS_KEY] ?: "[]"
            result = try {
                Script.listFromJson(json)
            } catch (e: Exception) {
                emptyList()
            }
        }
        return result
    }
}
