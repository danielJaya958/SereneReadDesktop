package com.sereneread.project.feature.library.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UploadBookDialog(
    form: UploadForm,
    onChange: (UploadForm) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val enabled = !form.isUploading
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text("Upload PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    form.filePath.substringAfterLast('/').substringAfterLast('\\'),
                    style = MaterialTheme.typography.bodyMedium,
                )
                BookFields(
                    title = form.title,
                    author = form.author,
                    year = form.year,
                    enabled = enabled,
                    onTitleChange = { onChange(form.copy(title = it)) },
                    onAuthorChange = { onChange(form.copy(author = it)) },
                    onYearChange = { onChange(form.copy(year = it)) },
                )
                if (form.isUploading) {
                    LinearProgressIndicator(progress = { form.progress }, modifier = Modifier.fillMaxWidth())
                }
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = form.canSubmit) { Text("Upload") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
