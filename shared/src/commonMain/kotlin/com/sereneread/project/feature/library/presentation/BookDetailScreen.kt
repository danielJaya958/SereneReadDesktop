package com.sereneread.project.feature.library.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sereneread.project.feature.library.domain.RemoteBook
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BookDetailRoute(
    bookId: Long,
    onBack: () -> Unit,
    onRead: (Long) -> Unit,
    viewModel: BookDetailViewModel = koinViewModel { parametersOf(bookId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

    BookDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onRead = { onRead(bookId) },
        onEdit = viewModel::onEditClick,
        onDelete = viewModel::onDeleteClick,
        onFavoriteToggle = viewModel::onFavoriteToggle,
    )

    state.editForm?.let { form ->
        EditBookDialog(
            form = form,
            onChange = viewModel::onEditChange,
            onSubmit = viewModel::onEditSubmit,
            onDismiss = viewModel::onEditDismiss,
        )
    }
    if (state.isConfirmingDelete) {
        DeleteBookDialog(
            title = state.book?.title.orEmpty(),
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onDeleteDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    state: BookDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail buku") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Kembali") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                state.book != null -> BookInfo(state.book, state, onRead, onEdit, onDelete, onFavoriteToggle)
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> Column(Modifier.align(Alignment.Center)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Coba lagi") }
                }
            }
        }
    }
}

@Composable
private fun BookInfo(
    book: RemoteBook,
    state: BookDetailUiState,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(book.title, style = MaterialTheme.typography.headlineMedium)
        Text("${book.author} (${book.publishedYear})", style = MaterialTheme.typography.bodyLarge)
        if (book.totalPages > 0) {
            Text("Halaman ${book.lastReadPage} dari ${book.totalPages}")
            LinearProgressIndicator(
                progress = { book.lastReadPage.toFloat() / book.totalPages },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("Sedang diproses server")
        }
        if (book.isFavorite) Text("Favorit", style = MaterialTheme.typography.labelLarge)

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRead, enabled = !state.isDeleting) { Text("Baca") }
            OutlinedButton(onClick = onFavoriteToggle, enabled = !state.isDeleting && !state.isSavingFavorite) {
                Text(if (book.isFavorite) "Hapus dari favorit" else "Tambah ke favorit")
            }
            OutlinedButton(onClick = onEdit, enabled = !state.isDeleting) { Text("Edit") }
            OutlinedButton(onClick = onDelete, enabled = !state.isDeleting) {
                Text(if (state.isDeleting) "Menghapus..." else "Hapus")
            }
        }
    }
}

@Composable
private fun EditBookDialog(
    form: EditForm,
    onChange: (EditForm) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val enabled = !form.isSaving
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text("Edit buku") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BookFields(
                    title = form.title,
                    author = form.author,
                    year = form.year,
                    enabled = enabled,
                    onTitleChange = { onChange(form.copy(title = it)) },
                    onAuthorChange = { onChange(form.copy(author = it)) },
                    onYearChange = { onChange(form.copy(year = it)) },
                )
                form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = form.canSubmit) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) { Text("Batal") }
        },
    )
}

@Composable
private fun DeleteBookDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus buku?") },
        text = {
            Text("\"$title\" akan dihapus dari server beserta file PDF, progres, dan favoritnya. Tindakan ini tidak bisa dibatalkan.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
