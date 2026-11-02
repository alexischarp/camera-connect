package app.cameraconnect.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cameraconnect.R
import app.cameraconnect.discovery.DiscoveryPhase
import app.cameraconnect.discovery.LogEntry
import app.cameraconnect.discovery.LogLevel
import app.cameraconnect.ui.appContainer
import app.cameraconnect.wifi.CameraConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Hidden screen driving the protocol discovery and showing its log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: DebugViewModel = viewModel { DebugViewModel(container) }
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var manualUrl by rememberSaveable { mutableStateOf("") }
    val notConnectedMessage = stringResource(R.string.debug_not_connected)
    val copiedMessage = stringResource(R.string.debug_logs_copied)

    fun guarded(action: () -> Unit) {
        if (viewModel.isConnected) action() else scope.launch { snackbarHostState.showSnackbar(notConnectedMessage) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.debug_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.exportLogs { file ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, file.name)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, file.name))
                            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.debug_logs_saved, file.absolutePath)) }
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.debug_share_logs))
                    }
                    IconButton(onClick = viewModel::clearLogs) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.debug_clear_logs))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConnectionBanner(connectionState)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { guarded(viewModel::runFullDiscovery) }, enabled = !report.running) {
                    Text(stringResource(R.string.debug_run_full))
                }
                OutlinedButton(onClick = { guarded(viewModel::runSsdpOnly) }, enabled = !report.running) {
                    Text(stringResource(R.string.debug_run_ssdp))
                }
                OutlinedButton(onClick = { guarded(viewModel::runPtpIpOnly) }, enabled = !report.running) {
                    Text(stringResource(R.string.debug_run_ptp))
                }
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("CameraConnect logs", viewModel.exportText()))
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                }) {
                    Text(stringResource(R.string.debug_copy_logs))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    label = { Text(stringResource(R.string.debug_manual_url_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { guarded { viewModel.loadDescription(manualUrl) } },
                    enabled = manualUrl.isNotBlank() && !report.running,
                ) {
                    Text(stringResource(R.string.debug_manual_url_load))
                }
            }

            if (report.running || report.phase != DiscoveryPhase.IDLE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (report.running) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(report.phase.labelRes()), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (report.containers.isNotEmpty()) {
                Text(stringResource(R.string.debug_containers_title), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(report.containers, key = { it.container.id }) { known ->
                        AssistChip(
                            onClick = { guarded { viewModel.browse(known.container.id) } },
                            enabled = !report.running,
                            label = {
                                Text(
                                    stringResource(
                                        R.string.debug_container_chip,
                                        known.container.title ?: "?",
                                        known.container.id,
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            LogList(entries = entries, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConnectionBanner(state: CameraConnectionState) {
    val text = when (state) {
        is CameraConnectionState.Connected -> stringResource(
            R.string.debug_connection_connected,
            state.link.primaryIpv4?.hostAddress ?: state.link.interfaceName ?: "?",
        )
        else -> stringResource(R.string.debug_connection_disconnected)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LogList(entries: List<LogEntry>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }
    if (entries.isEmpty()) {
        Text(stringResource(R.string.debug_empty), modifier = modifier, style = MaterialTheme.typography.bodySmall)
        return
    }
    val format = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    LazyColumn(state = listState, modifier = modifier) {
        items(entries) { entry ->
            Text(
                text = "${format.format(Date(entry.timestampMillis))} ${entry.level.name.first()}/${entry.tag}: ${entry.message}",
                color = entry.level.color(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LogLevel.color(): Color = when (this) {
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

private fun DiscoveryPhase.labelRes(): Int = when (this) {
    DiscoveryPhase.IDLE -> R.string.debug_phase_idle
    DiscoveryPhase.SSDP -> R.string.debug_phase_ssdp
    DiscoveryPhase.DESCRIPTION -> R.string.debug_phase_description
    DiscoveryPhase.SCPD -> R.string.debug_phase_scpd
    DiscoveryPhase.BROWSE -> R.string.debug_phase_browse
    DiscoveryPhase.PTP_IP -> R.string.debug_phase_ptpip
    DiscoveryPhase.DONE -> R.string.debug_phase_done
}
