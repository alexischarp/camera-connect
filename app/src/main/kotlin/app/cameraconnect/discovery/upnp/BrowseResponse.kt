package app.cameraconnect.discovery.upnp

import app.cameraconnect.discovery.upnp.XmlDom.childText
import app.cameraconnect.discovery.upnp.XmlDom.findFirst
import app.cameraconnect.discovery.upnp.XmlDom.localNameOrTag

sealed interface BrowseResponse {
    data class Success(
        /** Raw DIDL-Lite document (already unescaped). */
        val result: String,
        val numberReturned: Int,
        val totalMatches: Int,
        val updateId: String?,
    ) : BrowseResponse

    data class Fault(
        val errorCode: Int?,
        val errorDescription: String?,
        val faultString: String?,
    ) : BrowseResponse
}

/** Parses the SOAP envelope answering a ContentDirectory Browse (or Search) action. */
object BrowseResponseParser {

    fun parse(xml: String): BrowseResponse {
        val document = XmlDom.parse(xml)
        val body = document.documentElement.findFirst("Body")
            ?: throw UpnpParseException("No SOAP Body")
        body.findFirst("Fault")?.let { fault ->
            val error = fault.findFirst("UPnPError")
            return BrowseResponse.Fault(
                errorCode = error?.childText("errorCode")?.toIntOrNull(),
                errorDescription = error?.childText("errorDescription"),
                faultString = fault.childText("faultstring"),
            )
        }
        val response = body.findFirst("BrowseResponse")
            ?: body.findFirst("SearchResponse")
            ?: XmlDom.run { body.childElements().firstOrNull { it.localNameOrTag.endsWith("Response") } }
            ?: throw UpnpParseException("No BrowseResponse in SOAP Body")
        return BrowseResponse.Success(
            result = response.childText("Result").orEmpty(),
            numberReturned = response.childText("NumberReturned")?.toIntOrNull() ?: 0,
            totalMatches = response.childText("TotalMatches")?.toIntOrNull() ?: 0,
            updateId = response.childText("UpdateID"),
        )
    }
}
