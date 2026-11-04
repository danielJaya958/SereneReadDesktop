package com.sereneread.project.feature.library.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.sereneread.project.core.ui.archive.ArchiveField

/** The metadata fields shared by the upload and edit dialogs. */
@Composable
fun BookFields(
    title: String,
    author: String,
    year: String,
    enabled: Boolean,
    onTitleChange: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onYearChange: (String) -> Unit,
) {
    ArchiveField(title, onTitleChange, "Judul", Modifier.fillMaxWidth(), enabled)
    ArchiveField(author, onAuthorChange, "Pengarang", Modifier.fillMaxWidth(), enabled)
    ArchiveField(
        value = year,
        onValueChange = { value -> onYearChange(value.filter(Char::isDigit).take(4)) },
        label = "Tahun terbit",
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
