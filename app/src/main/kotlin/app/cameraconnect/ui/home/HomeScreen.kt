package app.cameraconnect.ui.home

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cameraconnect.R
import app.cameraconnect.camera.CameraSessionState
import app.cameraconnect.camera.SupportedCamera
import app.cameraconnect.locale.AppLanguage
import app.cameraconnect.ui.theme.BrandMark
import app.cameraconnect.ui.theme.PrecisionCard
import app.cameraconnect.ui.theme.precisionGrain
import app.cameraconnect.ui.appContainer
import app.cameraconnect.ui.permissions.WifiPermissions
import app.cameraconnect.wifi.CameraConnectionState
import app.cameraconnect.wifi.DisconnectReason
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Home screen: connection status, one Wi-Fi tutorial card per supported camera, the connection
 * form, then the camera session card with the gallery button. A long press on the title opens the
 * hidden debug screen; the top bar's language menu switches the UI between the device language,
 * English and French.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(onOpenDebug: () -> Unit, onOpenGallery: () -> Unit) {
    val container = appContainer()
    val viewModel: HomeViewModel = viewModel { HomeViewModel(container) }
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val ssid by viewModel.ssid.collectAsStateWithLifecycle()
    val passphrase by viewModel.passphrase.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showRationale by rememberSaveable { mutableStateOf(false) }
    val permissionDenied = stringResource(R.string.permission_denied)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) {
            viewModel.connect()
        } else {
            scope.launch { snackbarHostState.showSnackbar(permissionDenied) }
        }
    }

    fun requestConnection() {
        if (WifiPermissions.allGranted(context)) viewModel.connect() else showRationale = true
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.permission_title)) },
            text = {
                Text(
                    stringResource(
                        if (WifiPermissions.usesLocation) R.string.permission_rationale_location
                        else R.string.permission_rationale_nearby,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(WifiPermissions.required.toTypedArray())
                }) { Text(stringResource(R.string.permission_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text(stringResource(R.string.permission_cancel)) }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onOpenDebug),
                    ) {
                        BrandMark(Modifier.size(36.dp))
                        Text(
                            text = stringResource(R.string.home_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                },
                actions = { LanguageMenu() },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .precisionGrain()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.home_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectionStatusCard(
                state = connectionState,
                onDisconnect = viewModel::disconnect,
            )

            when (connectionState) {
                is CameraConnectionState.Connected -> {
                    CameraSessionCard(
                        state = sessionState,
                        onOpenGallery = onOpenGallery,
                        onRetry = viewModel::retrySession,
                    )
                }
                else -> {
                    SupportedCamera.entries.forEach { camera -> TutorialCard(camera) }
                    ConnectionForm(
                        ssidExample = SupportedCamera.entries.first().ssidExample,
                        ssid = ssid,
                        passphrase = passphrase,
                        connecting = connectionState is CameraConnectionState.Connecting,
                        onSsidChange = viewModel::onSsidChange,
                        onPassphraseChange = viewModel::onPassphraseChange,
                        onConnect = ::requestConnection,
                    )
                }
            }
        }
    }
}

/** Shows the language the UI is currently in and lets the user pick another one. */
@Composable
private fun LanguageMenu() {
    val locales = appContainer().locales
    val activity = LocalActivity.current
    val current = remember(locales) { locales.current() }
    val shown = LocalContext.current.resources.configuration.locales[0].language.uppercase(Locale.ROOT)
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(shown)
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.home_language_change))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(stringResource(language.labelRes)) },
                    trailingIcon = if (language == current) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        if (language != current) locales.apply(language, activity)
                    },
                )
            }
        }
    }
}

private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.FRENCH -> R.string.language_french
    }

@Composable
private fun ConnectionStatusCard(state: CameraConnectionState, onDisconnect: () -> Unit) {
    PrecisionCard(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_status_title), style = MaterialTheme.typography.titleMedium)
            when (state) {
                is CameraConnectionState.Disconnected -> {
                    val message = when (state.reason) {
                        DisconnectReason.LOST -> R.string.home_status_lost
                        DisconnectReason.UNAVAILABLE -> R.string.home_status_unavailable
                        DisconnectReason.ERROR -> R.string.home_status_error
                        DisconnectReason.NONE, DisconnectReason.USER -> R.string.home_status_disconnected
                    }
                    Text(stringResource(message))
                }
                is CameraConnectionState.Connecting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.home_status_connecting))
                    }
                }
                is CameraConnectionState.Connected -> {
                    Text(stringResource(R.string.home_status_connected))
                    state.ssid?.let { Text(stringResource(R.string.home_network_ssid, it)) }
                    Text(
                        text = stringResource(
                            R.string.home_network_details,
                            state.link.interfaceName ?: "?",
                            state.link.primaryIpv4?.hostAddress ?: "?",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.home_network_candidates,
                            state.link.candidateHosts.joinToString { it.hostAddress.orEmpty() }.ifEmpty { "-" },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onDisconnect) { Text(stringResource(R.string.home_disconnect)) }
                }
            }
        }
    }
}

@Composable
private fun CameraSessionCard(state: CameraSessionState, onOpenGallery: () -> Unit, onRetry: () -> Unit) {
    PrecisionCard(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                CameraSessionState.NoNetwork, CameraSessionState.Discovering -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.home_camera_searching))
                    }
                }
                is CameraSessionState.Ready -> {
                    Text(
                        text = stringResource(
                            R.string.home_camera_ready,
                            state.session.displayName,
                            state.session.modelName ?: "",
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.session.supportedModel == null) {
                        Text(stringResource(R.string.home_camera_untested), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onOpenGallery, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                        Text(stringResource(R.string.home_open_gallery))
                    }
                }
                is CameraSessionState.Failed -> {
                    val message = when (state.reason) {
                        CameraSessionState.FailureReason.NETWORK_BLOCKED -> R.string.home_camera_failed_network_blocked
                        CameraSessionState.FailureReason.NO_SSDP_ANSWER -> R.string.home_camera_failed_ssdp
                        CameraSessionState.FailureReason.NO_DESCRIPTION -> R.string.home_camera_failed_description
                        CameraSessionState.FailureReason.NO_CONTENT_DIRECTORY -> R.string.home_camera_failed_no_directory
                    }
                    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.home_retry)) }
                }
            }
        }
    }
}

/** How to turn on the Wi-Fi of one supported [camera]: its own menu path, then the steps shared by every model. */
@Composable
private fun TutorialCard(camera: SupportedCamera) {
    PrecisionCard(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_tutorial_title, camera.displayName), style = MaterialTheme.typography.titleMedium)
            val steps = camera.tutorialSteps.map { stringResource(it) } +
                stringResource(R.string.home_tutorial_step_ssid, camera.ssidExample) +
                stringResource(R.string.home_tutorial_step_connect)
            steps.forEachIndexed { index, step ->
                Row {
                    Text("${index + 1}.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(28.dp))
                    Text(step)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_tutorial_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Menu path on the camera, specific to each model; the SSID and connect steps are shared. */
private val SupportedCamera.tutorialSteps: List<Int>
    get() = when (this) {
        SupportedCamera.SONY_A7R_IV -> listOf(
            R.string.tutorial_sony_a7r_iv_step_1,
            R.string.tutorial_sony_a7r_iv_step_2,
            R.string.tutorial_sony_a7r_iv_step_3,
        )
    }

@Composable
private fun ConnectionForm(
    ssidExample: String,
    ssid: String,
    passphrase: String,
    connecting: Boolean,
    onSsidChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    var showPassphrase by rememberSaveable { mutableStateOf(false) }
    PrecisionCard(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.home_connection_details), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = ssid,
                onValueChange = onSsidChange,
                label = { Text(stringResource(R.string.home_ssid_label)) },
                placeholder = { Text(ssidExample) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.home_ssid_help), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = passphrase,
                onValueChange = onPassphraseChange,
                label = { Text(stringResource(R.string.home_password_label)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassphrase = !showPassphrase }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(
                                if (showPassphrase) R.string.home_password_hide else R.string.home_password_show,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onConnect, enabled = !connecting, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.home_connect))
            }
        }
    }
}
