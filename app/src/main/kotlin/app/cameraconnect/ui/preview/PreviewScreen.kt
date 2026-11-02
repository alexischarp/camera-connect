package app.cameraconnect.ui.preview

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cameraconnect.R
import app.cameraconnect.camera.CameraPhoto
import app.cameraconnect.download.DownloadState
import app.cameraconnect.ui.appContainer
import app.cameraconnect.ui.theme.Graphite
import app.cameraconnect.ui.theme.precisionGrain
import coil3.compose.SubcomposeAsyncImage

/** Full-screen photo with pinch zoom and a download button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(photoId: String, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: PreviewViewModel = viewModel(key = photoId) { PreviewViewModel(container, photoId) }
    val photo = viewModel.photo
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(downloadState) {
        val done = downloadState as? DownloadState.Done ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(R.string.preview_saved, done.folder))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(photo?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.debug_back))
                    }
                },
            )
        },
        bottomBar = {
            if (photo != null) {
                Surface(color = Graphite) {
                    DownloadBar(photo = photo, state = downloadState, onDownload = viewModel::download)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (photo == null) {
                Text(stringResource(R.string.preview_missing), modifier = Modifier.padding(24.dp))
            } else {
                ZoomableImage(url = photo.preview?.url ?: photo.thumbnail?.url, contentDescription = photo.title)
            }
        }
    }
}

@Composable
private fun ZoomableImage(url: String?, contentDescription: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    offset = if (newScale <= MIN_SCALE) Offset.Zero else offset + pan
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > MIN_SCALE) MIN_SCALE else DOUBLE_TAP_SCALE
                    offset = Offset.Zero
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            loading = { CircularProgressIndicator(color = Color.White) },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun DownloadBar(photo: CameraPhoto, state: DownloadState, onDownload: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.precisionGrain(Graphite).navigationBarsPadding().padding(20.dp)) {
        val original = photo.original
        when {
            photo.isRawOnly -> Text(stringResource(R.string.preview_raw_notice), style = MaterialTheme.typography.bodySmall)
            original != null -> Text(
                text = stringResource(
                    R.string.preview_original_info,
                    original.resolutionLabel ?: "?",
                    original.sizeBytes?.let { Formatter.formatShortFileSize(context, it) } ?: "?",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            else -> Text(stringResource(R.string.preview_original_unknown), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        when (state) {
            is DownloadState.InProgress -> {
                val fraction = state.fraction
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.preview_downloading, (fraction * 100).toInt()), style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.preview_downloading_bytes, Formatter.formatShortFileSize(context, state.bytesRead)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            is DownloadState.Done -> {
                Text(
                    text = stringResource(R.string.preview_saved_detail, state.folder, Formatter.formatShortFileSize(context, state.bytes)),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.preview_download_again))
                }
            }
            is DownloadState.Failed -> {
                Text(
                    text = stringResource(R.string.preview_failed, state.message),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.preview_download)) }
            }
            DownloadState.Idle -> Button(
                onClick = onDownload,
                enabled = photo.downloadRendition != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.preview_download)) }
        }
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f
