package app.cameraconnect.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.connectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "connection")

data class ConnectionSettings(
    val ssid: String,
    val passphrase: String,
)

/** Persists the camera Wi-Fi credentials and the identity presented during PTP/IP handshakes. */
class ConnectionPreferences(private val context: Context) {

    private object Keys {
        val SSID = stringPreferencesKey("ssid")
        val PASSPHRASE = stringPreferencesKey("passphrase")
        val PTP_GUID = stringPreferencesKey("ptp_guid")
    }

    val settings: Flow<ConnectionSettings> = context.connectionDataStore.data.map { prefs ->
        ConnectionSettings(
            ssid = prefs[Keys.SSID].orEmpty(),
            passphrase = prefs[Keys.PASSPHRASE].orEmpty(),
        )
    }

    suspend fun save(ssid: String, passphrase: String) {
        context.connectionDataStore.edit { prefs ->
            prefs[Keys.SSID] = ssid
            prefs[Keys.PASSPHRASE] = passphrase
        }
    }

    /** Stable per-install GUID so the camera sees the same PTP/IP initiator every time. */
    suspend fun ptpGuid(): UUID {
        val stored = context.connectionDataStore.data.first()[Keys.PTP_GUID]
        stored?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }?.let { return it }
        val fresh = UUID.randomUUID()
        context.connectionDataStore.edit { prefs -> prefs[Keys.PTP_GUID] = fresh.toString() }
        return fresh
    }
}
