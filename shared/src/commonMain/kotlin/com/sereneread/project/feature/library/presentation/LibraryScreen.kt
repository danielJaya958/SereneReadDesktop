package com.sereneread.project.feature.library.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sereneread.project.core.ui.archive.ArchiveButton
import com.sereneread.project.core.ui.archive.ArchiveField
import com.sereneread.project.core.ui.archive.ArchiveHeader
import com.sereneread.project.core.ui.archive.ArchiveLink
import com.sereneread.project.core.ui.archive.ArchivePill
import com.sereneread.project.core.ui.archive.FooterNote
import com.sereneread.project.core.ui.archive.MetaLine
import com.sereneread.project.core.ui.archive.coverKey
import com.sereneread.project.core.ui.archive.sharedCover
import com.sereneread.project.core.ui.theme.Archive
import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.RemoteBook
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val SpineWidth = 56.dp
private val SpineHeight = 340.dp
private val CoverWidth = 230.dp
private val CoverHeight = 360.dp

@Composable
fun LibraryRoute(
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
        loadCover = covers::load,
        onOpen = { book -> book.remoteId?.let(onOpenRemoteBook) },
        onActiveChange = viewModel::onActiveChange,
        onFilterChange = viewModel::onFilterChange,
        onQueryChange = viewModel::onQueryChange,
        onSortChange = viewModel::onSortChange,
        onLoadMore = viewModel::onLoadMore,
        onRetryRemote = viewModel::onRetryRemote,
        onUploadClick = viewModel::onUploadClick,
        onMessageShown = viewModel::onMessageShown,
        onOpenStats = onOpenStats,
        onSignOut = onSignOut,
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

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    loadCover: suspend (String) -> ImageBitmap?,
    onOpen: (ShelfBook) -> Unit,
    onActiveChange: (String) -> Unit,
    onFilterChange: (ShelfFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (BookSort) -> Unit,
    onLoadMore: () -> Unit,
    onRetryRemote: () -> Unit,
    onUploadClick: () -> Unit,
    onMessageShown: () -> Unit,
    onOpenStats: () -> Unit,
    onSignOut: () -> Unit,
) {
    val books = remember(state.remoteBooks, state.downloaded, state.filter, state.query) { shelfBooks(state) }
    val activeIndex = books.indexOfFirst { it.key == state.activeKey }.takeIf { it >= 0 } ?: 0
    val active = books.getOrNull(activeIndex)
    val focus = remember { FocusRequester() }

    // A lambda value, not a local fun: Compose memoizes references to local functions without their
    // captured values, which left the wheel stepping from the first book it ever saw.
    val move: (Int) -> Unit = { step -> books.getOrNull(activeIndex + step)?.let { onActiveChange(it.key) } }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(5_000)
            onMessageShown()
        }
    }
    // Keep paging while the reader walks toward the end of the server books.
    val remoteCount = if (state.filter == ShelfFilter.ALL) state.remoteBooks.size else 0
    LaunchedEffect(activeIndex, remoteCount, state.hasMoreRemote, state.isLoadingRemote) {
        if (state.hasMoreRemote && !state.isLoadingRemote && remoteCount > 0 && activeIndex >= remoteCount - 3) onLoadMore()
    }

    Box(Modifier.fillMaxSize().background(Archive.Canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { move(-1); true }
                        Key.DirectionRight -> { move(1); true }
                        Key.Enter, Key.NumPadEnter -> { active?.let(onOpen); active != null }
                        else -> false
                    }
                },
        ) {
            ArchiveHeader(
                actions = {
                    ArchiveLink("Statistik", onOpenStats)
                    ArchiveLink("Unggah PDF", onUploadClick)
                    ArchiveLink("Keluar", onSignOut, color = Archive.Muted)
                },
            )

            ShelfControls(state, onFilterChange, onQueryChange, onSortChange)

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    // The caption sits right under the shelf, so the title reads as the active book's label.
                    active != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Shelf(books, activeIndex, loadCover, onActiveChange, onOpen, onStep = move)
                        Caption(active, canGoBack = activeIndex > 0, canGoForward = activeIndex < books.lastIndex, move)
                    }
                    state.filter == ShelfFilter.DOWNLOADED && state.query.isBlank() -> ShelfMessage(
                        "Belum ada buku yang diunduh. Buka sebuah buku, lalu pilih Unduh untuk dibaca offline.",
                    )
                    state.isLoadingRemote && state.filter == ShelfFilter.ALL -> ShelfMessage("Menata rak...")
                    state.remoteError != null && state.filter == ShelfFilter.ALL -> ShelfMessage(state.remoteError) {
                        ArchiveButton("Coba lagi", onRetryRemote)
                    }
                    state.query.isNotBlank() -> ShelfMessage("Tidak ada buku yang cocok dengan \"${state.query.trim()}\".")
                    state.isOffline -> ShelfMessage("Tidak ada koneksi, dan belum ada buku yang diunduh untuk dibaca offline.")
                    else -> ShelfMessage("Rak masih kosong. Unggah PDF untuk mulai membaca.") {
                        ArchiveButton("Unggah PDF", onUploadClick, filled = true)
                    }
                }
            }

            FooterNote(
                text = state.message ?: footerText(state, books.size),
                isError = state.message == null && state.remoteError != null && books.isNotEmpty(),
                modifier = Modifier.padding(start = 32.dp, bottom = 20.dp, top = 12.dp),
            )
        }
    }
}

private fun footerText(state: LibraryUiState, count: Int): String = when {
    state.pendingSync > 0 && state.isOffline ->
        "Tidak ada koneksi. ${state.pendingSync} catatan baca tersimpan dan akan dikirim saat online."
    state.pendingSync > 0 -> "${state.pendingSync} catatan baca menunggu dikirim ke server."
    state.isOffline -> "Tidak ada koneksi. Rak hanya berisi buku yang sudah diunduh."
    state.remoteError != null && count > 0 -> state.remoteError
    count == 0 -> ""
    state.hasMoreRemote && state.filter == ShelfFilter.ALL -> "$count buku dimuat. Geser ke kanan untuk memuat lebih banyak."
    else -> "$count buku di rak."
}

private fun shelfBooks(state: LibraryUiState): List<ShelfBook> {
    val saved = state.downloaded.mapTo(HashSet()) { it.id }
    return when (state.filter) {
        ShelfFilter.ALL -> state.remoteBooks.map { it.toShelf(isDownloaded = it.id in saved) }
        ShelfFilter.DOWNLOADED -> {
            val needle = state.query.trim()
            state.downloaded
                .filter { needle.isEmpty() || it.title.contains(needle, true) || it.author.contains(needle, true) }
                .map { it.toShelf(isDownloaded = true) }
        }
    }
}

internal fun RemoteBook.toShelf(isDownloaded: Boolean = false) = ShelfBook(
    key = "remote-$id",
    title = title,
    author = author,
    year = publishedYear,
    format = "PDF",
    progressText = if (totalPages > 0) "Halaman $lastReadPage dari $totalPages" else "Sedang diproses server",
    thumbnailUrl = thumbnailUrl,
    isFavorite = isFavorite,
    remoteId = id,
    isDownloaded = isDownloaded,
)

@Composable
private fun ShelfControls(
    state: LibraryUiState,
    onFilterChange: (ShelfFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (BookSort) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShelfFilter.entries.forEach { filter ->
                ArchivePill(filter.label, selected = state.filter == filter) { onFilterChange(filter) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ArchiveField(
                value = state.query,
                onValueChange = onQueryChange,
                label = "Cari judul atau pengarang",
                modifier = Modifier.width(360.dp),
            )
            if (state.filter == ShelfFilter.ALL) SortMenu(state.sort, onSortChange)
        }
    }
}

private val ShelfFilter.label: String
    get() = when (this) {
        ShelfFilter.ALL -> "Semua"
        ShelfFilter.DOWNLOADED -> "Diunduh"
    }

@Composable
private fun SortMenu(sort: BookSort, onSortChange: (BookSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ArchiveLink("Urutkan: ${sort.label}", { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BookSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = MaterialTheme.typography.bodyMedium) },
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
private fun ShelfMessage(text: String, action: @Composable () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(460.dp),
        )
        action()
    }
}

/** One spring moves the whole shelf, so sliding, opening, and flipping can never drift apart. */
private val ShelfSpring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
private val SpineGap = 5.dp

/** How far book [index] is opened at shelf position [position]: 1 when centred, 0 one book away. */
private fun openness(position: Float, index: Int): Float = (1f - abs(position - index)).coerceIn(0f, 1f)

/**
 * Spines stand in a tight row; the active book opens out to its front cover in the middle.
 *
 * The shelf is laid out from a single animated position instead of a scrolling list. A scrolling list
 * clamps at its ends and fights items that change width while it scrolls, which made the shelf jump
 * near the first and last books. Only the books near the middle are composed, so long shelves stay light.
 */
@Composable
private fun Shelf(
    books: List<ShelfBook>,
    activeIndex: Int,
    loadCover: suspend (String) -> ImageBitmap?,
    onActiveChange: (String) -> Unit,
    onOpen: (ShelfBook) -> Unit,
    onStep: (Int) -> Unit,
) {
    val position = remember { Animatable(activeIndex.toFloat()) }
    // A new target keeps the current velocity, so quick wheel notches glide instead of restarting.
    LaunchedEffect(activeIndex) { position.animateTo(activeIndex.toFloat(), ShelfSpring) }
    val step by rememberUpdatedState(onStep)

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(CoverHeight + 40.dp).clipToBounds().wheelSteps { step(it) },
    ) {
        val at = position.value
        val reach = (maxWidth / (SpineWidth + SpineGap)).toInt() / 2 + 2
        val first = (floor(at).toInt() - reach).coerceAtLeast(0)
        val last = (ceil(at).toInt() + reach).coerceAtMost(books.lastIndex)

        Layout(
            content = {
                for (index in first..last) {
                    val book = books[index]
                    key(book.key) {
                        ShelfItem(
                            book = book,
                            number = index + 1,
                            open = openness(at, index),
                            active = index == activeIndex,
                            loadCover = loadCover,
                            onClick = { if (index == activeIndex) onOpen(book) else onActiveChange(book.key) },
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            val pitch = (SpineWidth + SpineGap).toPx()
            val extra = (CoverWidth - SpineWidth).toPx()

            // Left edge of book [index]: whole spines before it, plus the extra width of any opened book.
            fun left(index: Int): Float {
                var x = index * pitch
                for (open in floor(at).toInt()..ceil(at).toInt()) {
                    if (open < index) x += extra * openness(at, open)
                }
                return x
            }
            fun centre(index: Int): Float = left(index) + (SpineWidth.toPx() + extra * openness(at, index)) / 2

            val base = floor(at).toInt().coerceIn(0, books.lastIndex)
            val next = (base + 1).coerceAtMost(books.lastIndex)
            val fraction = (at - base).coerceIn(0f, 1f)
            val focus = centre(base) + (centre(next) - centre(base)) * fraction
            val shift = constraints.maxWidth / 2f - focus

            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { offset, placeable ->
                    val index = first + offset
                    placeable.place(
                        x = (left(index) + shift).roundToInt(),
                        y = (constraints.maxHeight - placeable.height) / 2,
                    )
                }
            }
        }
    }
}

/**
 * Turns the mouse wheel into one-book steps, so the active book always lands in the middle.
 * Vertical and horizontal wheels both work, and touchpads add up small deltas until a full notch.
 */
private fun Modifier.wheelSteps(onStep: (Int) -> Unit): Modifier = pointerInput(Unit) {
    var pending = 0f
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            event.changes.forEach { change ->
                pending += change.scrollDelta.y + change.scrollDelta.x
                change.consume()
            }
            while (pending >= 1f) {
                onStep(1)
                pending -= 1f
            }
            while (pending <= -1f) {
                onStep(-1)
                pending += 1f
            }
        }
    }
}

/**
 * [open] comes from the shelf position: the spine turns away over the first half and the cover turns in
 * over the second, so the book opens exactly as fast as it reaches the middle (DESIGN.md 6A).
 */
@Composable
private fun ShelfItem(
    book: ShelfBook,
    number: Int,
    open: Float,
    active: Boolean,
    loadCover: suspend (String) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(3.dp)

    Box(
        Modifier
            .width(lerp(SpineWidth, CoverWidth, open))
            .height(lerp(SpineHeight, CoverHeight, open))
            .then(if (open > 0f) Modifier.shadow(16.dp * open, shape, ambientColor = Archive.Shadow, spotColor = Archive.Shadow) else Modifier)
            .clickable(interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = listOfNotNull(book.title, book.author).joinToString(", ") },
    ) {
        if (open < 0.5f) {
            BookSpine(
                book,
                number,
                loadCover,
                Modifier.fillMaxSize().graphicsLayer {
                    rotationY = open * 180f
                    cameraDistance = 16f * density
                },
            )
        } else {
            val cover = Modifier.fillMaxSize().graphicsLayer {
                rotationY = (open - 1f) * 180f
                cameraDistance = 16f * density
            }
            BookFront(
                book,
                loadCover,
                if (active && book.remoteId != null) cover.sharedCover(coverKey(book.remoteId)) else cover,
            )
            if (hovered && active) OpenCue(Modifier.align(Alignment.Center))
        }
    }
}

/** The caption rolls like an odometer when the active book changes (DESIGN.md 6B). */
@Composable
private fun Caption(
    book: ShelfBook,
    canGoBack: Boolean,
    canGoForward: Boolean,
    move: (Int) -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp, start = 32.dp, end = 32.dp)) {
        AnimatedContent(
            targetState = book,
            contentKey = { it.key },
            transitionSpec = {
                val easing = tween<Float>(280, easing = FastOutSlowInEasing)
                (slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(easing)) togetherWith
                    (slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeOut(easing))
            },
            modifier = Modifier.align(Alignment.Center),
        ) { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    current.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(520.dp),
                    textAlign = TextAlign.Center,
                )
                MetaLine(
                    listOfNotNull(
                        current.author,
                        if (current.isFavorite) "Favorit" else null,
                        if (current.isDownloaded) "Tersimpan offline" else null,
                    ).joinToString("  ·  "),
                )
                Text(
                    listOfNotNull(current.year?.toString(), current.format, current.progressText).joinToString("  /  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = Archive.Muted,
                )
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ArrowButton("←", "Buku sebelumnya", canGoBack) { move(-1) }
            ArrowButton("→", "Buku berikutnya", canGoForward) { move(1) }
        }
    }
}

@Composable
private fun ArrowButton(arrow: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            arrow,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Default),
            color = if (enabled) Archive.Ink else Archive.Line,
        )
    }
}
