package app.cameraconnect.discovery

import app.cameraconnect.data.ConnectionPreferences
import app.cameraconnect.discovery.ptpip.PtpIp
import app.cameraconnect.discovery.ptpip.PtpIpProbe
import app.cameraconnect.discovery.ptpip.PtpIpProbeResult
import app.cameraconnect.discovery.ssdp.SsdpClient
import app.cameraconnect.discovery.upnp.BrowseFlag
import app.cameraconnect.discovery.upnp.BrowseOutcome
import app.cameraconnect.discovery.upnp.BrowseResponse
import app.cameraconnect.discovery.upnp.ContentDirectoryClient
import app.cameraconnect.discovery.upnp.DeviceDescription
import app.cameraconnect.discovery.upnp.DeviceDescriptionParser
import app.cameraconnect.discovery.upnp.DidlLite
import app.cameraconnect.discovery.upnp.ScpdParser
import app.cameraconnect.discovery.upnp.UpnpParseException
import app.cameraconnect.discovery.upnp.UpnpService
import app.cameraconnect.network.CameraHttpClient
import app.cameraconnect.wifi.CameraLink
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Runs the protocol discovery against the connected camera and writes everything it learns into
 * the [DiscoveryLogger]. Nothing about the camera is assumed: addresses come from the Wi-Fi link,
 * URLs from SSDP and the device description, action names from the SCPD.
 */
class DiscoveryOrchestrator(
    private val logger: DiscoveryLogger,
    private val preferences: ConnectionPreferences,
    private val ssdpClient: SsdpClient,
    private val http: OkHttpClient,
) {

    private val ptpIpProbe = PtpIpProbe(logger)
    private val mutex = Mutex()

    private val _report = MutableStateFlow(DiscoveryReport())
    val report: StateFlow<DiscoveryReport> = _report.asStateFlow()

    /** SSDP → device description → SCPD → Browse walk, then PTP/IP when UPnP gave nothing usable. */
    suspend fun runFullDiscovery(link: CameraLink) = exclusive("full discovery") {
        _report.value = DiscoveryReport(running = true)
        logLink(link)

        val responses = runSsdp(link)
        val locations = responses.mapNotNull { it.message.location }.distinct()
        if (locations.isEmpty()) {
            logger.w(TAG, "No LOCATION header received: no UPnP device answered")
        }
        val devices = locations.mapNotNull { url -> describeDevice(http, url) }
        val contentDirectory = selectContentDirectory(devices)
        if (contentDirectory != null) {
            exploreContentDirectory(http, contentDirectory.first, contentDirectory.second)
        } else {
            logger.w(TAG, "No ContentDirectory service found; falling back to PTP/IP")
            runPtpIp(link)
        }
        summarize()
    }

    suspend fun runSsdpOnly(link: CameraLink) = exclusive("SSDP search") {
        _report.update { it.copy(running = true) }
        logLink(link)
        runSsdp(link)
        setPhase(DiscoveryPhase.DONE)
    }

    suspend fun runPtpIpOnly(link: CameraLink) = exclusive("PTP/IP probe") {
        _report.update { it.copy(running = true) }
        logLink(link)
        runPtpIp(link)
        setPhase(DiscoveryPhase.DONE)
    }

    /** Fetches a description URL typed by the user, then explores its ContentDirectory if any. */
    suspend fun loadDescription(link: CameraLink, url: String) = exclusive("manual description") {
        _report.update { it.copy(running = true) }
        val device = describeDevice(http, url.trim())
        val contentDirectory = device?.let { selectContentDirectory(listOf(it)) }
        if (contentDirectory != null) {
            exploreContentDirectory(http, contentDirectory.first, contentDirectory.second)
        } else if (device != null) {
            logger.w(TAG, "Description loaded but it exposes no ContentDirectory service")
        }
        setPhase(DiscoveryPhase.DONE)
    }

    /** Browses one container chosen on the debug screen using the ContentDirectory found earlier. */
    suspend fun browse(link: CameraLink, objectId: String) = exclusive("browse $objectId") {
        val service = _report.value.contentDirectory
        if (service == null) {
            logger.w(TAG, "Cannot browse \"$objectId\": no ContentDirectory service discovered yet")
            return@exclusive
        }
        _report.update { it.copy(running = true, phase = DiscoveryPhase.BROWSE) }
        val client = ContentDirectoryClient(http, service, logger)
        val depth = _report.value.containers.firstOrNull { it.container.id == objectId }?.depth ?: 0
        browseAndLog(client, objectId, depth, BrowseFlag.METADATA)
        val outcome = browseAndLog(client, objectId, depth, BrowseFlag.DIRECT_CHILDREN)
        outcome.didl?.let { rememberContainers(it, depth + 1) }
        setPhase(DiscoveryPhase.DONE)
    }

    // ---------------------------------------------------------------------------------------------

    private suspend fun exclusive(name: String, block: suspend () -> Unit) {
        if (mutex.isLocked) {
            logger.w(TAG, "Ignoring \"$name\": another discovery step is still running")
            return
        }
        mutex.withLock {
            logger.i(TAG, "===== $name: start =====")
            try {
                block()
            } catch (e: CancellationException) {
                logger.w(TAG, "$name cancelled")
                throw e
            } catch (e: Exception) {
                logger.e(TAG, "$name aborted: $e")
            } finally {
                _report.update { it.copy(running = false) }
                logger.i(TAG, "===== $name: end =====")
            }
        }
    }

    private fun setPhase(phase: DiscoveryPhase) = _report.update { it.copy(phase = phase) }

    private fun logLink(link: CameraLink) {
        logger.i(TAG, "Camera link:\n${link.describe()}")
    }

    private suspend fun runSsdp(link: CameraLink): List<SsdpClient.Response> {
        setPhase(DiscoveryPhase.SSDP)
        val candidates = link.candidateHosts
        logger.i(
            TAG,
            "SSDP M-SEARCH for ${SEARCH_TARGETS.size} search targets, multicast + unicast to " +
                candidates.joinToString { it.hostAddress.orEmpty() }.ifEmpty { "(no unicast candidates)" },
        )
        val responses = try {
            ssdpClient.search(
                link = link,
                searchTargets = SEARCH_TARGETS,
                unicastTargets = candidates,
                listenMillis = SSDP_LISTEN_MS,
                userAgent = CameraHttpClient.USER_AGENT,
            )
        } catch (e: SsdpClient.CameraNetworkBlockedException) {
            logger.e(TAG, "The phone refuses to use the camera network; a VPN is probably active. Disable it and retry.")
            emptyList()
        }
        _report.update { it.copy(ssdpResponses = responses) }
        if (responses.isEmpty()) {
            logger.w(TAG, "No SSDP response. Either the camera does not run UPnP in this mode, or the phone drops multicast.")
        } else {
            val summary = responses.joinToString("\n") { response ->
                "${response.from.hostAddress}  ST/NT=${response.message.searchTarget}  LOCATION=${response.message.location}  SERVER=${response.message.server}"
            }
            logger.i(TAG, "SSDP responders:\n$summary")
        }
        return responses
    }

    private suspend fun describeDevice(http: OkHttpClient, url: String): DescribedDevice? {
        setPhase(DiscoveryPhase.DESCRIPTION)
        val xml = fetchText(http, url) ?: return null
        logger.d(TAG, "Device description from $url:\n${xml.take(MAX_RAW_LOG_CHARS)}")
        val description = try {
            DeviceDescriptionParser.parse(xml, url)
        } catch (e: UpnpParseException) {
            logger.e(TAG, "Cannot parse device description $url: ${e.message}")
            return null
        }
        logger.i(TAG, "Device at $url:\n${description.summary()}")
        val described = DescribedDevice(url, description)
        _report.update { it.copy(devices = it.devices + described) }
        return described
    }

    private fun selectContentDirectory(devices: List<DescribedDevice>): Pair<DeviceDescription, UpnpService>? =
        devices.firstNotNullOfOrNull { described ->
            described.description.findService("ContentDirectory")?.let { described.description to it }
        }

    private suspend fun exploreContentDirectory(http: OkHttpClient, device: DeviceDescription, service: UpnpService) {
        _report.update { it.copy(contentDirectoryDevice = device, contentDirectory = service) }
        logger.i(TAG, "Using ContentDirectory ${service.serviceType}\n  control=${service.controlUrl}\n  SCPD=${service.scpdUrl}")

        setPhase(DiscoveryPhase.SCPD)
        val scpdUrl = service.scpdUrl
        if (scpdUrl != null) {
            fetchText(http, scpdUrl)?.let { xml ->
                logger.d(TAG, "SCPD from $scpdUrl:\n${xml.take(MAX_RAW_LOG_CHARS)}")
                runCatching { ScpdParser.parse(xml) }
                    .onSuccess { scpd ->
                        _report.update { it.copy(scpd = scpd) }
                        logger.i(TAG, "ContentDirectory actions (${scpd.actions.size}):\n" +
                            scpd.actions.joinToString("\n") { "  " + it.signature() })
                        val sortCaps = scpd.stateVariables.firstOrNull { it.name == "A_ARG_TYPE_SortCriteria" }
                        if (sortCaps != null) logger.d(TAG, "SortCriteria variable: ${sortCaps.dataType} ${sortCaps.allowedValues}")
                        if (!scpd.hasAction("Browse")) logger.w(TAG, "SCPD has no Browse action!")
                    }
                    .onFailure { logger.e(TAG, "Cannot parse SCPD: $it") }
            }
        } else {
            logger.w(TAG, "ContentDirectory has no SCPDURL")
        }

        setPhase(DiscoveryPhase.BROWSE)
        val client = ContentDirectoryClient(http, service, logger)
        browseAndLog(client, ROOT_OBJECT_ID, 0, BrowseFlag.METADATA)
        walk(client)
    }

    /** Breadth-first walk of the hierarchy, bounded so the camera is not flooded. */
    private suspend fun walk(client: ContentDirectoryClient) {
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = HashSet<String>()
        queue += ROOT_OBJECT_ID to 0
        var calls = 0
        while (queue.isNotEmpty() && calls < MAX_WALK_CALLS) {
            val (objectId, depth) = queue.removeFirst()
            if (!visited.add(objectId)) continue
            calls++
            val outcome = browseAndLog(client, objectId, depth, BrowseFlag.DIRECT_CHILDREN)
            val didl = outcome.didl ?: continue
            rememberContainers(didl, depth + 1)
            if (depth + 1 <= MAX_WALK_DEPTH) {
                didl.containers.forEach { queue += it.id to depth + 1 }
            }
        }
        if (queue.isNotEmpty()) {
            logger.i(TAG, "Walk stopped after $calls Browse calls; ${queue.size} container(s) left unexplored (tap them on the debug screen)")
        }
    }

    private fun rememberContainers(didl: DidlLite, depth: Int) {
        if (didl.containers.isEmpty()) return
        _report.update { report ->
            val known = report.containers.associateBy { it.container.id }.toMutableMap()
            didl.containers.forEach { container -> known.putIfAbsent(container.id, KnownContainer(container, depth)) }
            report.copy(containers = known.values.toList())
        }
    }

    private suspend fun browseAndLog(
        client: ContentDirectoryClient,
        objectId: String,
        depth: Int,
        flag: BrowseFlag,
    ): BrowseOutcome {
        val outcome = client.browse(objectId, flag)
        val label = "Browse ${flag.wireValue} id=\"$objectId\" depth=$depth"
        if (outcome.error != null) {
            logger.e(TAG, "$label failed: ${outcome.error}")
            return outcome
        }
        logger.d(TAG, "$label raw response (HTTP ${outcome.httpCode}):\n${outcome.rawBody.take(MAX_RAW_LOG_CHARS)}")
        when (val response = outcome.response) {
            null -> logger.e(TAG, "$label: HTTP ${outcome.httpCode}, unparseable SOAP response")
            is BrowseResponse.Fault -> logger.w(
                TAG,
                "$label: UPnP fault ${response.errorCode} ${response.errorDescription} (${response.faultString})",
            )
            is BrowseResponse.Success -> {
                val didl = outcome.didl
                logger.i(
                    TAG,
                    "$label: HTTP ${outcome.httpCode} NumberReturned=${response.numberReturned} " +
                        "TotalMatches=${response.totalMatches} UpdateID=${response.updateId}" +
                        (if (didl == null) " (DIDL-Lite not parsed)" else "\n" + describe(didl)),
                )
            }
        }
        return outcome
    }

    private fun describe(didl: DidlLite): String = buildString {
        for (container in didl.containers) {
            appendLine("  [container] id=\"${container.id}\" parent=\"${container.parentId}\" title=\"${container.title}\" class=${container.upnpClass} childCount=${container.childCount}")
            for ((name, value) in container.extras) appendLine("      $name=${value.take(200)}")
        }
        val items = didl.items
        for (item in items.take(MAX_ITEMS_LOGGED)) {
            appendLine("  [item] id=\"${item.id}\" parent=\"${item.parentId}\" title=\"${item.title}\" class=${item.upnpClass} date=${item.date}")
            item.albumArtUri?.let { appendLine("      albumArtURI=$it") }
            for (resource in item.resources) appendLine("      res: ${resource.summary()}")
            for ((name, value) in item.extras) appendLine("      $name=${value.take(200)}")
        }
        if (items.size > MAX_ITEMS_LOGGED) {
            appendLine("  ... ${items.size - MAX_ITEMS_LOGGED} more item(s) not listed")
        }
        val resourceStats = items.flatMap { it.resources }
            .groupBy { "${it.mimeType ?: "?"} ${it.dlnaProfile ?: ""}".trim() }
            .map { (kind, resources) -> "$kind x${resources.size} (${resources.mapNotNull { it.resolution }.distinct().take(5)})" }
        if (resourceStats.isNotEmpty()) appendLine("  resource kinds: " + resourceStats.joinToString("; "))
        val nonImage = items.filterNot { it.isImage }
        if (nonImage.isNotEmpty()) appendLine("  non-image items: ${nonImage.size} (classes ${nonImage.mapNotNull { it.upnpClass }.distinct()})")
    }.trimEnd()

    private suspend fun runPtpIp(link: CameraLink) {
        setPhase(DiscoveryPhase.PTP_IP)
        val candidates = link.candidateHosts
        if (candidates.isEmpty()) {
            logger.w(TAG, "PTP/IP: no candidate host (no DHCP server, gateway or DNS reported by the link)")
            return
        }
        val guid = preferences.ptpGuid()
        val results = mutableListOf<PtpIpProbeResult>()
        for (host in candidates) {
            logger.i(TAG, "PTP/IP probe ${host.hostAddress}:${PtpIp.DEFAULT_PORT}")
            val result = ptpIpProbe.probe(link, host, PtpIp.DEFAULT_PORT, guid, PTP_FRIENDLY_NAME)
            results += result
            _report.update { it.copy(ptpIp = results.toList()) }
            if (result.handshakeSucceeded) break
        }
    }

    private fun summarize() {
        setPhase(DiscoveryPhase.DONE)
        val report = _report.value
        val lines = buildList {
            add("SSDP responses: ${report.ssdpResponses.size}")
            add("UPnP devices described: ${report.devices.size}" +
                report.devices.joinToString("") { "\n  - ${it.description.friendlyName} (${it.description.modelName}) @ ${it.locationUrl}" })
            add("ContentDirectory: ${report.contentDirectory?.controlUrl ?: "none"}")
            add("Containers seen: ${report.containers.size}" +
                report.containers.joinToString("") { "\n  - depth ${it.depth} id=\"${it.container.id}\" title=\"${it.container.title}\" childCount=${it.container.childCount}" })
            val ptp = report.ptpIp
            add(
                when {
                    ptp.isEmpty() -> "PTP/IP: not tested"
                    ptp.any { it.handshakeSucceeded } -> "PTP/IP: handshake OK on ${ptp.first { it.handshakeSucceeded }.host}" +
                        (ptp.firstNotNullOfOrNull { it.deviceInfo }?.let { " (${it.manufacturer} ${it.model})" } ?: "")
                    ptp.any { it.tcpConnected } -> "PTP/IP: TCP open but handshake refused (${ptp.mapNotNull { it.initFailReason ?: it.error }})"
                    else -> "PTP/IP: no responder (${ptp.mapNotNull { it.error }.distinct()})"
                },
            )
        }
        logger.i(TAG, "SUMMARY\n" + lines.joinToString("\n"))
    }

    private suspend fun fetchText(http: OkHttpClient, url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", CameraHttpClient.USER_AGENT).build()
            http.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    logger.w(TAG, "GET $url returned HTTP ${response.code}:\n${body.take(1000)}")
                    null
                } else {
                    body
                }
            }
        } catch (e: IOException) {
            logger.e(TAG, "GET $url failed: $e")
            null
        } catch (e: IllegalArgumentException) {
            logger.e(TAG, "Invalid URL $url: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "Discovery"
        private const val SSDP_LISTEN_MS = 4_000L
        private const val MAX_RAW_LOG_CHARS = 12_000
        private const val MAX_ITEMS_LOGGED = 8
        private const val MAX_WALK_CALLS = 12
        private const val MAX_WALK_DEPTH = 3
        private const val PTP_FRIENDLY_NAME = "CameraConnect"

        /** UPnP defines "0" as the identifier of the root container. */
        const val ROOT_OBJECT_ID = "0"

        private val SEARCH_TARGETS = listOf(
            "ssdp:all",
            "upnp:rootdevice",
            "urn:schemas-upnp-org:device:MediaServer:1",
            "urn:schemas-upnp-org:service:ContentDirectory:1",
            "urn:schemas-sony-com:service:ScalarWebAPI:1",
        )
    }
}
