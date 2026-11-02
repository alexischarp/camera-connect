package app.cameraconnect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.cameraconnect.AppContainer
import app.cameraconnect.CameraConnectApp

/** Resolves the process-wide [AppContainer] from the composition's application context. */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as CameraConnectApp).container
