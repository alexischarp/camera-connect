package app.cameraconnect

import android.content.Context
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.cameraconnect.locale.AppLocales
import app.cameraconnect.ui.navigation.AppNavHost
import app.cameraconnect.ui.theme.CameraConnectTheme

class MainActivity : ComponentActivity() {

    /** Android 12 and below: apply the in-app language choice to this activity's resources. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            CameraConnectTheme {
                AppNavHost()
            }
        }
    }
}
