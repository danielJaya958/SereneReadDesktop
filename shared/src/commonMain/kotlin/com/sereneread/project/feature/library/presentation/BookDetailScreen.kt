package com.sereneread.project.feature.library.presentation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sereneread.project.core.ui.archive.ArchiveButton
import com.sereneread.project.core.ui.archive.ArchiveHeader
import com.sereneread.project.core.ui.archive.ArchiveLink
import com.sereneread.project.core.ui.archive.BackButton
import com.sereneread.project.core.ui.archive.LocalNavAnimatedScope
import com.sereneread.project.core.ui.archive.MetaLine
import com.sereneread.project.core.ui.archive.coverKey
import com.sereneread.project.core.ui.archive.sharedCover
import com.sereneread.project.core.ui.theme.Archive
import com.sereneread.project.feature.library.domain.RemoteBook
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun BookDetailRoute(
    bookId: Long,
    onBack: () -> Unit,
    onRead: (Long) -> Unit,
    viewModel: BookDetailViewModel = koinViewModel { parametersOf(bookId) },
    covers: BookCovers = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }
    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

    BookDetailScreen(
        bookId = bookId,
        state = state,
        loadCover = covers::load,
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

/** An asymmetric editorial split: the catalog text on the left, the lifted cover on the right. */
@Composable
fun BookDetailScreen(
    bookId: Long,
    state: BookDetailUiState,
    loadCover: suspend (String) -> ImageBitmap?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Escape -> { onBack(); true }
                        Key.Enter, Key.NumPadEnter -> { if (state.book != null) onRead(); state.book != null }
                        else -> false
                    }
                },
        ) {
            ArchiveHeader(start = { BackButton(onBack) })

            val book = state.book
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    book != null -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        CatalogText(book, state, Modifier.weight(1f), onRead, onEdit, onDelete, onFavoriteToggle)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Poster(book, loadCover)
                        }
                    }
                    state.isLoading -> Text("Membuka buku...", style = MaterialTheme.typography.headlineSmall)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(state.error.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                        ArchiveButton("Coba lagi", onRetry)
                    }
                }
            }
        }
    }
}

/** Fades and slides in a moment after the cover starts its flip, so the eye follows the cover first. */
@Composable
private fun CatalogText(
    book: RemoteBook,
    state: BookDetailUiState,
    modifier: Modifier,
    onRead: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val transition = LocalNavAnimatedScope.current?.transition
    val reveal = transition?.animateFloat(
        transitionSpec = { tween(350, delayMillis = 180) },
        label = "catalog text",
    ) { if (it == EnterExitState.Visible) 1f else 0f }?.value ?: 1f
    val busy = state.isDeleting || state.isSavingFavorite

    Column(
        modifier
            .padding(start = 64.dp, end = 32.dp)
            .widthIn(max = 520.dp)
            .graphicsLayer {
                alpha = reveal
                translationX = (1f - reveal) * -24.dp.toPx()
            },
    ) {
        MetaLine(book.author)
        Text(
            book.title,
            style = MaterialTheme.typography.displayLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        MetaLine(
            listOfNotNull(
                book.publishedYear.toString(),
                "PDF",
                if (book.totalPages > 0) "${book.totalPages} halaman" else null,
                if (book.isFavorite) "Favorit" else null,
            ).joinToString("  /  "),
            Modifier.padding(top = 16.dp),
        )
        Text(
            when {
                book.totalPages == 0 -> "Server masih menyiapkan buku ini. Anda tetap bisa mulai membaca."
                book.lastReadPage <= 1 -> "Belum mulai dibaca."
                book.lastReadPage >= book.totalPages -> "Selesai dibaca."
                else -> "Terakhir dibaca di halaman ${book.lastReadPage} dari ${book.totalPages}."
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 20.dp).widthIn(max = 420.dp),
        )
        Text(
            "Ditulis oleh ${book.author}.",
            style = MaterialTheme.typography.bodySmall,
            color = Archive.Muted,
            modifier = Modifier.padding(top = 16.dp),
        )

        state.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Archive.Oxblood, modifier = Modifier.padding(top = 16.dp))
        }

        Row(
            Modifier.padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArchiveButton(if (book.lastReadPage > 1) "Lanjut membaca" else "Mulai membaca", onRead, enabled = !state.isDeleting)
            ArchiveLink(if (book.isFavorite) "Hapus dari favorit" else "Jadikan favorit", onFavoriteToggle, enabled = !busy)
            ArchiveLink("Edit", onEdit, enabled = !busy)
            ArchiveLink(if (state.isDeleting) "Menghapus..." else "Hapus", onDelete, enabled = !busy, color = Archive.Muted)
        }
    }
}

/**
 * The cover arrives from the shelf and turns a full circle on the way, showing its paper back halfway
 * (DESIGN.md 6C). Going back plays the same turn in reverse.
 */
@Composable
private fun Poster(book: RemoteBook, loadCover: suspend (String) -> ImageBitmap?) {
    val transition = LocalNavAnimatedScope.current?.transition
    val turn = transition?.animateFloat(
        transitionSpec = { tween(700, easing = FastOutSlowInEasing) },
        label = "cover flip",
    ) { if (it == EnterExitState.Visible) 360f else 0f }?.value ?: 360f
    val showBack = turn % 360f in 90f..270f
    val shape = RoundedCornerShape(3.dp)
    val layer = Modifier.graphicsLayer {
        rotationY = if (showBack) turn - 180f else turn
        rotationZ = -6f * sin(turn / 360f * PI).toFloat()
        cameraDistance = 16f * density
    }

    Box(
        Modifier
            .size(300.dp, 460.dp)
            .sharedCover(coverKey(book.id))
            .then(layer)
            .shadow(16.dp, shape, ambientColor = Archive.Shadow, spotColor = Archive.Shadow),
    ) {
        if (showBack) {
            BookBack(Modifier.fillMaxSize())
        } else {
            BookFront(book.toShelf(), loadCover, Modifier.fillMaxSize(), titleSize = 34)
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
        title = { Text("Edit buku", style = MaterialTheme.typography.headlineSmall) },
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
                form.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Archive.Oxblood) }
            }
        },
        confirmButton = {
            ArchiveButton(if (form.isSaving) "Menyimpan..." else "Simpan", onSubmit, enabled = form.canSubmit, filled = true)
        },
        dismissButton = { ArchiveButton("Batal", onDismiss, enabled = enabled) },
    )
}

@Composable
private fun DeleteBookDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus buku?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "\"$title\" akan dihapus dari server beserta file PDF, progres, dan favoritnya. Tindakan ini tidak bisa dibatalkan.",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = { ArchiveButton("Hapus", onConfirm, filled = true) },
        dismissButton = { ArchiveButton("Batal", onDismiss) },
    )
}
