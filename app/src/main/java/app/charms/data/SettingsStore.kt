package app.charms.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

data class Settings(
    val onboarded: Boolean = false,
    /** The young sorcerer's name, for the grimoire's title page. */
    val name: String = "",
    /** The mentor's four digits. */
    val pin: String = "",
    /** Pages that have already lit up with a notification ("day:deed"), so none lights twice. */
    val fired: String = "",
    /** The last epoch day whose finished spells were shown, so a spell is celebrated once. */
    val spellsSeen: String = "",
    /** The capture in progress (deed id), so a camera trip survives the app being killed. */
    val pending: Long = 0,
)

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        onboarded = this[ONBOARDED] ?: false,
        name = this[NAME] ?: "",
        pin = this[PIN] ?: "",
        fired = this[FIRED] ?: "",
        spellsSeen = this[SPELLS] ?: "",
        pending = this[PENDING] ?: 0L,
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[ONBOARDED] = s.onboarded
            it[NAME] = s.name
            it[PIN] = s.pin
            it[FIRED] = s.fired
            it[SPELLS] = s.spellsSeen
            it[PENDING] = s.pending
        }
    }

    private companion object {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val NAME = stringPreferencesKey("name")
        val PIN = stringPreferencesKey("pin")
        val FIRED = stringPreferencesKey("fired")
        val SPELLS = stringPreferencesKey("spells_seen")
        val PENDING = longPreferencesKey("pending")
    }
}
