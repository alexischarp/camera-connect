package app.cameraconnect.camera

import app.cameraconnect.discovery.upnp.DeviceDescription
import app.cameraconnect.discovery.upnp.UpnpService
import app.cameraconnect.wifi.CameraLink

/** A camera found on the connected Wi-Fi network, with the ContentDirectory service to browse it. */
class CameraSession(
    val link: CameraLink,
    val descriptionUrl: String,
    val device: DeviceDescription,
    val contentDirectory: UpnpService,
) {
    val displayName: String get() = device.friendlyName ?: device.modelName ?: "Camera"
    val modelName: String? get() = device.modelName ?: device.modelDescription

    /** The tested model this camera is, or null for an untested camera (the app still tries to work with it). */
    val supportedModel: SupportedCamera? get() = SupportedCamera.find(device)

    /** Sub-folder of the phone gallery for this camera's photos: the model code, else a file-safe display name. */
    val folderName: String
        get() = supportedModel?.modelCode
            ?: displayName.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "Camera" }

    /** Container the vendor marks as the photo root, when the description says so (Sony: av:photoRoot). */
    val photoRootHint: String? get() = device.extras.entries
        .firstOrNull { it.key.endsWith("photoRoot", ignoreCase = true) }
        ?.value?.takeIf { it.isNotBlank() }
}

sealed interface CameraSessionState {
    data object NoNetwork : CameraSessionState
    data object Discovering : CameraSessionState
    data class Ready(val session: CameraSession) : CameraSessionState
    data class Failed(val reason: FailureReason) : CameraSessionState

    enum class FailureReason {
        /** The phone refuses to bind sockets to the camera network, typically because a VPN is active. */
        NETWORK_BLOCKED,
        NO_SSDP_ANSWER,
        NO_DESCRIPTION,
        NO_CONTENT_DIRECTORY,
    }
}
