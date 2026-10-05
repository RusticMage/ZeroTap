package com.zerotap.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotap.ServiceLocator
import com.zerotap.core.config.AppConfiguration
import com.zerotap.core.config.DeploymentMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = ServiceLocator.userPreferences
    private val credentialStore = ServiceLocator.secureCredentialStore

    private val _selectedMode = MutableStateFlow<DeploymentMode?>(null)
    val selectedMode: StateFlow<DeploymentMode?> = _selectedMode.asStateFlow()

    private val _byokKeyInput = MutableStateFlow("")
    val byokKeyInput: StateFlow<String> = _byokKeyInput.asStateFlow()

    private val _serverUrlInput = MutableStateFlow(AppConfiguration.backendBaseUrl)
    val serverUrlInput: StateFlow<String> = _serverUrlInput.asStateFlow()

    private val _serverEmailInput = MutableStateFlow("")
    val serverEmailInput: StateFlow<String> = _serverEmailInput.asStateFlow()

    private val _serverPasswordInput = MutableStateFlow("")
    val serverPasswordInput: StateFlow<String> = _serverPasswordInput.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun selectMode(mode: DeploymentMode) {
        _selectedMode.value = mode
        AppConfiguration.currentMode = mode
    }

    fun updateByokKey(key: String) {
        _byokKeyInput.value = key
    }

    fun updateServerUrl(url: String) {
        _serverUrlInput.value = url
        AppConfiguration.backendBaseUrl = url
    }

    fun updateServerEmail(email: String) {
        _serverEmailInput.value = email
    }

    fun updateServerPassword(pass: String) {
        _serverPasswordInput.value = pass
    }

    fun completePrivateMode(onSuccess: () -> Unit) {
        viewModelScope.launch {
            userPreferences.setDeploymentMode(DeploymentMode.PRIVATE)
            AppConfiguration.currentMode = DeploymentMode.PRIVATE

            val key = _byokKeyInput.value.trim()
            if (key.isNotEmpty()) {
                credentialStore.saveApiKey("BYOK_PROVIDER", key)
                userPreferences.setSelectedAiProvider("BYOK_PROVIDER")
            } else {
                userPreferences.setSelectedAiProvider("LOCAL")
            }

            userPreferences.setOnboardingCompleted(true)
            onSuccess()
        }
    }

    fun completeServerMode(onSuccess: () -> Unit) {
        viewModelScope.launch {
            userPreferences.setDeploymentMode(DeploymentMode.SERVER)
            AppConfiguration.currentMode = DeploymentMode.SERVER
            AppConfiguration.backendBaseUrl = _serverUrlInput.value.trim()

            userPreferences.setSelectedAiProvider("ZEROTAP_SERVER_VERTEX")
            userPreferences.setOnboardingCompleted(true)
            onSuccess()
        }
    }
}
