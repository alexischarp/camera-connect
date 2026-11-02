package app.cameraconnect.discovery

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/**
 * In-memory log shared by the discovery pipeline and the debug screen. Entries are also mirrored
 * to logcat under the "CameraConnect" prefix so `adb logcat` shows the same information.
 */
class DiscoveryLogger {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        _entries.update { current ->
            if (current.size >= MAX_ENTRIES) current.drop(current.size - MAX_ENTRIES + 1) + entry else current + entry
        }
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, "CameraConnect/$tag", message)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun exportText(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return buildString {
            for (entry in _entries.value) {
                append(format.format(Date(entry.timestampMillis)))
                append(' ')
                append(entry.level.name.padEnd(5))
                append(' ')
                append(entry.tag)
                append(": ")
                append(entry.message)
                append('\n')
            }
        }
    }

    private companion object {
        const val MAX_ENTRIES = 5000
    }
}
