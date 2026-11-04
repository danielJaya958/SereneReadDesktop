package com.sereneread.project.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sereneread.project.core.ui.archive.ArchiveButton
import com.sereneread.project.core.ui.archive.ArchiveField
import com.sereneread.project.core.ui.archive.ArchiveLink
import com.sereneread.project.core.ui.archive.MetaLine
import com.sereneread.project.core.ui.theme.Archive
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthRoute(
    sessionExpired: Boolean,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(
        state = state,
        sessionExpired = sessionExpired,
        onUsernameChange = viewModel::onUsernameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onModeChange = viewModel::onModeChange,
        onSubmit = viewModel::onSubmit,
    )
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    sessionExpired: Boolean,
    onUsernameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onModeChange: (AuthMode) -> Unit,
    onSubmit: () -> Unit,
) {
    val isLogin = state.mode == AuthMode.Login
    val enabled = !state.isSubmitting
    // ImeAction does not fire for a physical keyboard on desktop, so Enter is handled here.
    val fieldModifier = Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
        if (isEnter && event.type == KeyEventType.KeyDown) onSubmit()
        isEnter
    }

    Row(Modifier.fillMaxSize().background(Archive.Canvas), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(start = 64.dp, end = 32.dp)) {
            MetaLine("Rak baca pribadi")
            Text("SereneRead", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Buku Anda tersimpan di server, posisi baca ikut tersinkron, dan buku yang sudah diunduh tetap bisa dibaca tanpa koneksi.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 20.dp).widthIn(max = 420.dp),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(if (isLogin) "Masuk" else "Buat akun", style = MaterialTheme.typography.headlineMedium)

                if (sessionExpired && state.error == null) {
                    Text("Sesi berakhir. Silakan masuk lagi.", style = MaterialTheme.typography.bodySmall, color = Archive.Oxblood)
                }
                if (!isLogin) {
                    ArchiveField(state.username, onUsernameChange, "Username", fieldModifier, enabled)
                }
                ArchiveField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = "Email",
                    modifier = fieldModifier,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                ArchiveField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = "Password",
                    modifier = fieldModifier,
                    enabled = enabled,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )

                state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Archive.Oxblood) }

                ArchiveButton(
                    text = when {
                        state.isSubmitting -> "Memproses..."
                        isLogin -> "Masuk"
                        else -> "Daftar"
                    },
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    filled = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                ArchiveLink(
                    if (isLogin) "Belum punya akun? Daftar" else "Sudah punya akun? Masuk",
                    { onModeChange(if (isLogin) AuthMode.Register else AuthMode.Login) },
                    enabled = enabled,
                    color = Archive.Muted,
                )
            }
        }
    }
}
