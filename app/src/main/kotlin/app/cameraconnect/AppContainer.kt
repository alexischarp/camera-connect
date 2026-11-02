package app.cameraconnect

import android.content.Context
import android.net.wifi.WifiManager
import app.cameraconnect.camera.CameraSessionManager
import app.cameraconnect.camera.GalleryRepository
import app.cameraconnect.data.ConnectionPreferences
import app.cameraconnect.discovery.DiscoveryLogger
import app.cameraconnect.discovery.DiscoveryOrchestrator
import app.cameraconnect.discovery.ssdp.SsdpClient
import app.cameraconnect.download.PhotoDownloader
import app.cameraconnect.locale.AppLocales
import app.cameraconnect.network.CameraHttpClient
import app.cameraconnect.wifi.CameraConnectionState
import app.cameraconnect.wifi.CameraWifiConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-written dependency container. The app is small enough that a DI framework would only add
 * build time; everything lives for the whole process.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    /** Scope for work that must outlive a single screen, such as a discovery run or a download. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val logger = DiscoveryLogger()
    val preferences = ConnectionPreferences(appContext)
    val locales = AppLocales(appContext)
    val wifiConnector = CameraWifiConnector(appContext, logger)

    /** Dedicated client; every socket goes through the camera network, never the phone's default one. */
    val httpClient = CameraHttpClient(
        provider = { (wifiConnector.state.value as? CameraConnectionState.Connected)?.link?.network },
        logger = logger,
    )

    private val ssdpClient = SsdpClient(logger, appContext.getSystemService(WifiManager::class.java))

    val discovery = DiscoveryOrchestrator(logger, preferences, ssdpClient, httpClient.okHttp)
    val sessionManager = CameraSessionManager(appScope, wifiConnector, ssdpClient, httpClient.okHttp, logger)
    val gallery = GalleryRepository(httpClient.okHttp, logger)
    val downloader = PhotoDownloader(appContext, httpClient.okHttp, appScope, logger)
}
