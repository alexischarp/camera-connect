package app.cameraconnect.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cameraconnect.AppContainer
import app.cameraconnect.camera.CameraSessionState
import app.cameraconnect.wifi.CameraConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    val connectionState: StateFlow<CameraConnectionState> = container.wifiConnector.state
    val sessionState: StateFlow<CameraSessionState> = container.sessionManager.state

    private val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid.asStateFlow()

    private val _passphrase = MutableStateFlow("")
    val passphrase: StateFlow<String> = _passphrase.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = container.preferences.settings.first()
            if (_ssid.value.isEmpty()) _ssid.value = saved.ssid
            if (_passphrase.value.isEmpty()) _passphrase.value = saved.passphrase
        }
    }

    fun onSsidChange(value: String) {
        _ssid.value = value
    }

    fun onPassphraseChange(value: String) {
        _passphrase.value = value
    }

    fun connect() {
        val ssid = _ssid.value.trim()
        val passphrase = _passphrase.value
        viewModelScope.launch {
            container.preferences.save(ssid, passphrase)
            container.wifiConnector.connect(ssid, passphrase)
        }
    }

    fun disconnect() {
        container.wifiConnector.disconnect()
    }

    fun retrySession() {
        container.sessionManager.retry()
    }
}
