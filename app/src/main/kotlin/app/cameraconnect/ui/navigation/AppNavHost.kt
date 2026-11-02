package app.cameraconnect.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cameraconnect.ui.appContainer
import app.cameraconnect.ui.debug.DebugScreen
import app.cameraconnect.ui.gallery.GalleryScreen
import app.cameraconnect.ui.home.HomeScreen
import app.cameraconnect.ui.preview.PreviewScreen
import app.cameraconnect.wifi.CameraConnectionState

object Routes {
    const val HOME = "home"
    const val DEBUG = "debug"
    const val GALLERY = "gallery"
    const val PREVIEW = "preview/{photoId}"
    fun preview(photoId: String) = "preview/${Uri.encode(photoId)}"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val connection by appContainer().wifiConnector.state.collectAsStateWithLifecycle()

    // Camera gone (switched off, out of range, released): every camera screen falls back to home.
    LaunchedEffect(connection) {
        if (connection !is CameraConnectionState.Connected) {
            navController.popBackStack(Routes.HOME, inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDebug = { navController.navigate(Routes.DEBUG) },
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
            )
        }
        composable(Routes.DEBUG) {
            DebugScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GALLERY) {
            GalleryScreen(
                onBack = { navController.popBackStack() },
                onOpenPhoto = { id -> navController.navigate(Routes.preview(id)) },
            )
        }
        composable(
            route = Routes.PREVIEW,
            arguments = listOf(navArgument("photoId") { type = NavType.StringType }),
        ) { entry ->
            val photoId = entry.arguments?.getString("photoId").orEmpty()
            PreviewScreen(photoId = photoId, onBack = { navController.popBackStack() })
        }
    }
}
