package app.cameraconnect.discovery.upnp

import app.cameraconnect.discovery.DiscoveryLogger
import app.cameraconnect.network.CameraHttpClient
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class BrowseFlag(val wireValue: String) {
    METADATA("BrowseMetadata"),
    DIRECT_CHILDREN("BrowseDirectChildren"),
}

data class BrowseOutcome(
    val httpCode: Int,
    val rawBody: String,
    val response: BrowseResponse?,
    val didl: DidlLite?,
    val error: String?,
)

/** Anything that can answer a ContentDirectory Browse; implemented by the camera client and by test fakes. */
interface ContentBrowser {
    suspend fun browse(
        objectId: String,
        flag: BrowseFlag = BrowseFlag.DIRECT_CHILDREN,
        startingIndex: Int = 0,
        requestedCount: Int = ContentDirectoryClient.DEFAULT_PAGE_SIZE,
    ): BrowseOutcome
}

/**
 * Minimal SOAP client for the ContentDirectory service found during discovery. The control URL and
 * the service type both come from the device description, nothing is assumed about the camera.
 */
class ContentDirectoryClient(
    private val http: OkHttpClient,
    private val service: UpnpService,
    private val logger: DiscoveryLogger,
) : ContentBrowser {

    override suspend fun browse(
        objectId: String,
        flag: BrowseFlag,
        startingIndex: Int,
        requestedCount: Int,
    ): BrowseOutcome = browse(objectId, flag, startingIndex, requestedCount, filter = "*", sortCriteria = "")

    suspend fun browse(
        objectId: String,
        flag: BrowseFlag,
        startingIndex: Int,
        requestedCount: Int,
        filter: String,
        sortCriteria: String,
    ): BrowseOutcome = withContext(Dispatchers.IO) {
        val controlUrl = service.controlUrl
            ?: return@withContext BrowseOutcome(0, "", null, null, "Service has no controlURL")
        val body = buildBrowseEnvelope(service.serviceType, objectId, flag, startingIndex, requestedCount, filter, sortCriteria)
        val request = Request.Builder()
            .url(controlUrl)
            .header("SOAPAction", "\"${service.serviceType}#Browse\"")
            .header("User-Agent", CameraHttpClient.USER_AGENT)
            .post(body.toRequestBody(SOAP_MEDIA_TYPE))
            .build()
        logger.d(TAG, "Browse ${flag.wireValue} objectId=\"$objectId\" start=$startingIndex count=$requestedCount")
        try {
            http.newCall(request).execute().use { httpResponse ->
                val raw = httpResponse.body.string()
                val parsed = runCatching { BrowseResponseParser.parse(raw) }
                    .onFailure { logger.w(TAG, "Cannot parse SOAP response: $it") }
                    .getOrNull()
                val didl = (parsed as? BrowseResponse.Success)
                    ?.result?.takeIf { it.isNotBlank() }
                    ?.let { result ->
                        runCatching { DidlLiteParser.parse(result) }
                            .onFailure { logger.w(TAG, "Cannot parse DIDL-Lite: $it") }
                            .getOrNull()
                    }
                BrowseOutcome(httpResponse.code, raw, parsed, didl, null)
            }
        } catch (e: IOException) {
            BrowseOutcome(0, "", null, null, e.toString())
        }
    }

    companion object {
        private const val TAG = "ContentDirectory"
        const val DEFAULT_PAGE_SIZE = 100
        private val SOAP_MEDIA_TYPE = "text/xml; charset=\"utf-8\"".toMediaType()

        fun buildBrowseEnvelope(
            serviceType: String,
            objectId: String,
            flag: BrowseFlag,
            startingIndex: Int,
            requestedCount: Int,
            filter: String,
            sortCriteria: String,
        ): String = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
            append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
            append("<s:Body>")
            append("<u:Browse xmlns:u=\"").append(escapeXml(serviceType)).append("\">")
            append("<ObjectID>").append(escapeXml(objectId)).append("</ObjectID>")
            append("<BrowseFlag>").append(flag.wireValue).append("</BrowseFlag>")
            append("<Filter>").append(escapeXml(filter)).append("</Filter>")
            append("<StartingIndex>").append(startingIndex).append("</StartingIndex>")
            append("<RequestedCount>").append(requestedCount).append("</RequestedCount>")
            append("<SortCriteria>").append(escapeXml(sortCriteria)).append("</SortCriteria>")
            append("</u:Browse>")
            append("</s:Body>")
            append("</s:Envelope>")
        }

        fun escapeXml(value: String): String = buildString(value.length) {
            for (c in value) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }
    }
}
