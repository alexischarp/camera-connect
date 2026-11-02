package app.cameraconnect.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cameraconnect.R
import app.cameraconnect.camera.CameraPhoto
import app.cameraconnect.ui.appContainer
import app.cameraconnect.ui.theme.Charcoal
import app.cameraconnect.ui.theme.Ivory
import app.cameraconnect.ui.theme.precisionGrain
import coil3.compose.AsyncImage

/** Grid of the camera's photos, newest first, loading older pages as the user scrolls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit, onOpenPhoto: (String) -> Unit) {
    val container = appContainer()
    val viewModel: GalleryViewModel = viewModel { GalleryViewModel(container) }
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    val nearEnd by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= gridState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, ui.photos.size, ui.endReached) {
        if (nearEnd && ui.photos.isNotEmpty() && !ui.endReached) viewModel.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ui.cameraName ?: stringResource(R.string.gallery_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ui.photos.isNotEmpty()) {
                            Text(
                                text = pluralStringResource(R.plurals.gallery_photo_count, ui.photos.size, ui.photos.size),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.debug_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !ui.refreshing && !ui.loadingInitial) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.gallery_refresh))
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .precisionGrain(),
        ) {
            when {
                ui.photos.isEmpty() && ui.loadingInitial -> StatusBox {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.gallery_loading))
                }
                ui.photos.isEmpty() && ui.error != null -> StatusBox {
                    Text(stringResource(R.string.gallery_error, ui.error.orEmpty()), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = viewModel::refresh) { Text(stringResource(R.string.home_retry)) }
                }
                ui.photos.isEmpty() -> StatusBox { Text(stringResource(R.string.gallery_empty)) }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.photos, key = { it.id }) { photo ->
                        PhotoCell(photo = photo, onClick = { onOpenPhoto(photo.id) })
                    }
                    if (ui.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.gallery_loading_more), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (ui.error != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(R.string.gallery_error, ui.error.orEmpty()),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBox(content: @Composable () -> Unit) {
    // Scrollable so the pull-to-refresh gesture still works on an empty gallery.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(120.dp))
        content()
    }
}

@Composable
private fun PhotoCell(photo: CameraPhoto, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.thumbnail?.url,
            contentDescription = photo.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.isRawOnly) {
            Text(
                text = stringResource(R.string.gallery_raw_badge),
                style = MaterialTheme.typography.labelSmall,
                color = Ivory,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Charcoal.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

private const val LOAD_MORE_THRESHOLD = 15
