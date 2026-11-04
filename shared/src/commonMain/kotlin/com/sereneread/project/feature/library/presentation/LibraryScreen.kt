package com.sereneread.project.feature.library.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.sereneread.project.feature.library.domain.BookSort
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sereneread.project.feature.library.domain.Book
import com.sereneread.project.feature.library.domain.RemoteBook
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryRoute(
    onOpenBook: (Long) -> Unit,
    onOpenRemoteBook: (Long) -> Unit,
    onOpenStats: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
    covers: BookCovers = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.onScreenShown() }
    LibraryScreen(
        state = state,
        onImportClick = viewModel::onImportClick,
        onUploadClick = viewModel::onUploadClick,
        onOpenBook = onOpenBook,
        onOpenRemoteBook = onOpenRemoteBook,
        onOpenStats = onOpenStats,
        onSignOut = onSignOut,
        onDeleteBook = viewModel::onDeleteBook,
        onMessageShown = viewModel::onMessageShown,
        onQueryChange = viewModel::onQueryChange,
        onSortChange = viewModel::onSortChange,
        loadCover = covers::load,
        onLoadMore = viewModel::onLoadMore,
        onRetryRemote = viewModel::onRetryRemote,
    )
    state.uploadForm?.let { form ->
        UploadBookDialog(
            form = form,
            onChange = viewModel::onUploadFormChange,
            onSubmit = viewModel::onUploadSubmit,
            onDismiss = viewModel::onUploadDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onImportClick: () -> Unit,
    onUploadClick: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenRemoteBook: (Long) -> Unit,
    onOpenStats: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteBook: (Long) -> Unit,
    onMessageShown: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (BookSort) -> Unit,
    loadCover: suspend (String) -> ImageBitmap?,
    onLoadMore: () -> Unit,
    onRetryRemote: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    TextButton(onClick = onOpenStats) { Text("Statistik") }
                    TextButton(onClick = onSignOut) { Text("Keluar") }
                },
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(onClick = onImportClick) { Text("Tambah buku lokal") }
                ExtendedFloatingActionButton(onClick = onUploadClick) { Text("Upload PDF") }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("Cari judul atau pengarang") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Buku online") }
                    SortMenu(state.sort, onSortChange)
                }
            }
            if (state.isOffline) {
                item { Text("Tidak ada koneksi. Hanya buku yang sudah diunduh yang ditampilkan.") }
            }
            items(state.remoteBooks, key = { "remote-${it.id}" }) { book ->
                RemoteBookItem(book, loadCover, onClick = { onOpenRemoteBook(book.id) })
            }
            item {
                RemoteStatus(state, onLoadMore, onRetryRemote)
            }

            item { SectionTitle("Buku lokal") }
            when {
                state.isLoading -> item { CenteredProgress() }
                state.books.isEmpty() -> item {
                    Text("Belum ada buku lokal. Tambahkan file EPUB, PDF, atau TXT.")
                }
                else -> items(state.books, key = { "local-${it.id}" }) { book ->
                    BookItem(book, onClick = { onOpenBook(book.id) }, onDelete = { onDeleteBook(book.id) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun RemoteStatus(state: LibraryUiState, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    when {
        state.isLoadingRemote -> CenteredProgress()
        state.remoteError != null -> Column {
            Text(state.remoteError, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Coba lagi") }
        }
        state.hasMoreRemote -> TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
            Text("Muat lagi")
        }
        state.remoteBooks.isEmpty() -> Text(
            when {
                state.query.isNotBlank() -> "Buku tidak ditemukan."
                state.isOffline -> "Belum ada buku yang diunduh."
                else -> "Belum ada buku di server."
            },
        )
    }
}

@Composable
private fun RemoteBookItem(book: RemoteBook, loadCover: suspend (String) -> ImageBitmap?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(book, loadCover)
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium)
                Text("${book.author} (${book.publishedYear})", style = MaterialTheme.typography.bodyMedium)
                if (book.isFavorite) Text("Favorit", style = MaterialTheme.typography.labelMedium)
                if (book.totalPages > 0) {
                    Text(
                        "Halaman ${book.lastReadPage} dari ${book.totalPages}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { book.lastReadPage.toFloat() / book.totalPages },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Sedang diproses server", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Covers are 2:3 portraits; the box keeps that shape while loading or when there is no cover. */
@Composable
private fun BookCover(book: RemoteBook, loadCover: suspend (String) -> ImageBitmap?) {
    val url = book.thumbnailUrl
    val cover by produceState<ImageBitmap?>(null, url) { value = url?.let { loadCover(it) } }
    Box(
        Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        cover?.let {
            Image(it, contentDescription = "Sampul ${book.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun SortMenu(sort: BookSort, onSortChange: (BookSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("Urutkan: ${sort.label}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BookSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSortChange(option)
                    },
                )
            }
        }
    }
}

private val BookSort.label: String
    get() = when (this) {
        BookSort.NEWEST -> "Terbaru"
        BookSort.TITLE -> "Judul"
        BookSort.AUTHOR -> "Pengarang"
        BookSort.PUBLISHED_YEAR -> "Tahun terbit"
    }

@Composable
private fun BookItem(book: Book, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(book.format.name, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { book.progress }, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onDelete) { Text("Hapus") }
        }
    }
}
