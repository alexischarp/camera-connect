package app.cameraconnect.ui.debug

import androidx.lifecycle.ViewModel
import app.cameraconnect.AppContainer
import app.cameraconnect.discovery.DiscoveryReport
import app.cameraconnect.discovery.LogEntry
import app.cameraconnect.wifi.CameraConnectionState
import app.cameraconnect.wifi.CameraLink
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebugViewModel(private val container: AppContainer) : ViewModel() {

    val connectionState: StateFlow<CameraConnectionState> = container.wifiConnector.state
    val entries: StateFlow<List<LogEntry>> = container.logger.entries
    val report: StateFlow<DiscoveryReport> = container.discovery.report

    val isConnected: Boolean get() = currentLink() != null

    fun runFullDiscovery() = withLink { container.discovery.runFullDiscovery(it) }
    fun runSsdpOnly() = withLink { container.discovery.runSsdpOnly(it) }
    fun runPtpIpOnly() = withLink { container.discovery.runPtpIpOnly(it) }
    fun loadDescription(url: String) = withLink { container.discovery.loadDescription(it, url) }
    fun browse(objectId: String) = withLink { container.discovery.browse(it, objectId) }

    fun clearLogs() = container.logger.clear()

    fun exportText(): String = container.logger.exportText()

    /** Writes the log to the app's external files directory (readable with `adb pull`) and hands the file back. */
    fun exportLogs(onReady: (File) -> Unit) {
        container.appScope.launch {
            val file = withContext(Dispatchers.IO) {
                val directory = container.appContext.getExternalFilesDir("logs")
                    ?: File(container.appContext.filesDir, "logs")
                directory.mkdirs()
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                File(directory, "discovery-$stamp.log").apply { writeText(container.logger.exportText()) }
            }
            withContext(Dispatchers.Main) { onReady(file) }
        }
    }

    private fun currentLink(): CameraLink? =
        (connectionState.value as? CameraConnectionState.Connected)?.link

    /** Discovery runs on the app scope so leaving the screen does not cancel it. */
    private fun withLink(block: suspend (CameraLink) -> Unit) {
        val link = currentLink()
        if (link == null) {
            container.logger.w("Debug", "Not connected to the camera network; connect from the home screen first")
            return
        }
        container.appScope.launch { block(link) }
    }
}
