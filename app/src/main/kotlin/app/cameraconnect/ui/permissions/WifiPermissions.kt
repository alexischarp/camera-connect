package app.cameraconnect.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime permissions needed to reach the camera over its Wi-Fi network, depending on the OS version. */
object WifiPermissions {

    /** Android 17 (API 37) gates all local-network traffic behind ACCESS_LOCAL_NETWORK. */
    private const val LOCAL_NETWORK_PERMISSION_SDK = 37

    val required: List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_PERMISSION_SDK) {
            add(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }

    val usesLocation: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    fun allGranted(context: Context): Boolean = required.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
