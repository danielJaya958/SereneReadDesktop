package com.sereneread.project.feature.library.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sereneread.project.core.ui.archive.ArchiveButton
import com.sereneread.project.core.ui.archive.MetaLine
import com.sereneread.project.core.ui.theme.Archive

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
        title = { Text("Unggah PDF", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetaLine(form.filePath.substringAfterLast('/').substringAfterLast('\\'))
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
                    LinearProgressIndicator(
                        progress = { form.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Archive.Ink,
                        trackColor = Archive.Line,
                    )
                }
                form.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Archive.Oxblood) }
            }
        },
        confirmButton = {
            ArchiveButton(if (form.isUploading) "Mengunggah..." else "Unggah", onSubmit, enabled = form.canSubmit, filled = true)
        },
        dismissButton = { ArchiveButton("Batal", onDismiss) },
    )
}
