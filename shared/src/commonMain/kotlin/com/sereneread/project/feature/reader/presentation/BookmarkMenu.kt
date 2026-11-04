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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.sereneread.project.core.ui.archive.ArchiveButton
import com.sereneread.project.core.ui.archive.ArchiveField
import com.sereneread.project.core.ui.archive.ArchiveLink
import com.sereneread.project.core.ui.theme.Archive
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
        ArchiveLink(if (bookmarks.isEmpty()) "Bookmark" else "Bookmark (${bookmarks.size})", { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (isMarked) "Ubah bookmark halaman ini" else "Tandai halaman ini", style = MaterialTheme.typography.labelMedium) },
                onClick = {
                    expanded = false
                    onMarkPage()
                },
            )
            error?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Archive.Oxblood,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (bookmarks.isNotEmpty()) HorizontalDivider()
            bookmarks.forEach { bookmark ->
                DropdownMenuItem(
                    text = {
                        Text(
                            bookmark.label(),
                            style = MaterialTheme.typography.bodyMedium,
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
        title = { Text("Bookmark halaman ${form.page}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                ArchiveField(
                    value = form.note,
                    onValueChange = onNoteChange,
                    label = "Catatan (opsional)",
                    enabled = !form.isSaving,
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                form.error?.let {
                    Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Archive.Oxblood)
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (form.bookmarkId != null) {
                    ArchiveLink("Hapus", onDelete, enabled = !form.isSaving, color = Archive.Muted)
                }
                Spacer(Modifier.weight(1f))
                ArchiveButton("Batal", onDismiss, enabled = !form.isSaving)
                Spacer(Modifier.width(12.dp))
                ArchiveButton(if (form.isSaving) "Menyimpan..." else "Simpan", onSave, enabled = !form.isSaving, filled = true)
            }
        },
    )
}
