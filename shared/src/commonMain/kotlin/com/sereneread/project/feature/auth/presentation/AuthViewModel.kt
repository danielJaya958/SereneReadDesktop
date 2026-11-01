package com.sereneread.project.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.core.ui.toUserMessage
import com.sereneread.project.feature.auth.domain.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onModeChange(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null) }

    fun onSubmit() {
        val form = _state.value
        if (!form.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            val email = form.email.trim()
            if (form.mode == AuthMode.Register) {
                val registered = repository.register(form.username.trim(), email, form.password)
                if (registered is AppResult.Failure) {
                    _state.update { it.copy(isSubmitting = false, error = registered.error.toUserMessage()) }
                    return@launch
                }
            }

            when (val login = repository.login(email, form.password)) {
                // This ViewModel outlives the sign-in, so the password must not stay in its state.
                is AppResult.Success -> _state.value = AuthUiState()
                is AppResult.Failure -> _state.update {
                    // The account now exists, so a second register would only return 409.
                    it.copy(mode = AuthMode.Login, isSubmitting = false, error = login.error.toUserMessage())
                }
            }
        }
    }
}
