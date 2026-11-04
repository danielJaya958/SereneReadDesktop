package com.sereneread.project.feature.reader.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sereneread.project.feature.reader.domain.Bookmark
import kotlinx.coroutines.launch

@Composable
fun BookmarkMenu(
    bookmarks: List<Bookmark>,
    currentPage: Int,
    error: String?,
    listState: LazyListState,
    onMarkPage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val isMarked = bookmarks.any { it.page == currentPage }

    Box {
        TextButton(onClick = { expanded = true }) { Text("Bookmark") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (isMarked) "Ubah bookmark halaman ini" else "Tandai halaman ini") },
                onClick = {
                    expanded = false
                    onMarkPage()
                },
            )
            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (bookmarks.isNotEmpty()) HorizontalDivider()
            bookmarks.forEach { bookmark ->
                DropdownMenuItem(
                    text = {
                        Text(
                            bookmark.label(),
                            modifier = Modifier.width(280.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        scope.launch { listState.scrollToItem(bookmark.page - 1) }
                    },
                )
            }
        }
    }
}

private fun Bookmark.label() = if (note == null) "Halaman $page" else "Halaman $page: $note"

@Composable
fun BookmarkDialog(
    form: BookmarkForm,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bookmark halaman ${form.page}") },
        text = {
            Column {
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNoteChange,
                    label = { Text("Catatan (opsional)") },
                    enabled = !form.isSaving,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                form.error?.let {
                    Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth()) {
                if (form.bookmarkId != null) {
                    TextButton(onClick = onDelete, enabled = !form.isSaving) { Text("Hapus") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !form.isSaving) { Text("Batal") }
                Button(onClick = onSave, enabled = !form.isSaving) { Text("Simpan") }
            }
        },
    )
}
