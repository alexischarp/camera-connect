package app.cameraconnect.camera

import app.cameraconnect.discovery.upnp.DeviceDescription

/**
 * Camera models the app was tested with. The app speaks plain UPnP, so an unlisted camera can
 * still work: this list only drives the home screen tutorials, the SSID example, the "untested
 * model" notice and the name of the download folder. To add a model, see docs/adding-a-camera.md.
 */
enum class SupportedCamera(
    /** Name shown to the user. */
    val displayName: String,
    /** Model code the camera reports about itself; also names its folder in the phone gallery. */
    val modelCode: String,
    /** SSID of the camera's access point, as the camera displays it. */
    val ssidExample: String,
) {
    SONY_A7R_IV(displayName = "Sony A7R IV", modelCode = "ILCE-7RM4", ssidExample = "DIRECT-xxxx:ILCE-7RM4");

    /** True when [device] identifies itself with this model code (the ILCE-7RM4 puts it in friendlyName). */
    fun matches(device: DeviceDescription): Boolean =
        listOfNotNull(device.friendlyName, device.modelName, device.modelDescription, device.modelNumber)
            .any { it.contains(modelCode, ignoreCase = true) }

    companion object {
        fun find(device: DeviceDescription): SupportedCamera? = entries.firstOrNull { it.matches(device) }
    }
}
