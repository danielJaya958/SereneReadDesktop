package com.sereneread.project.feature.reader.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sereneread.project.core.ui.archive.ArchiveLink
import com.sereneread.project.core.ui.archive.BackButton
import com.sereneread.project.core.ui.archive.MetaLine
import com.sereneread.project.core.ui.theme.Archive
import com.sereneread.project.feature.reader.domain.BookContent
import com.sereneread.project.feature.reader.domain.Chapter
import com.sereneread.project.feature.reader.domain.PageSize
import com.sereneread.project.feature.reader.domain.Paragraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun ReaderRoute(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel { parametersOf(bookId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReaderScreen(
        state = state,
        onBack = onBack,
        onProgressChanged = viewModel::onProgressChanged,
        renderPage = viewModel::renderPage,
    )
}

@Composable
fun RemoteReaderRoute(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: RemoteReaderViewModel = koinViewModel { parametersOf(bookId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isLeaving by remember { mutableStateOf(false) }

    ReaderScreen(
        state = state,
        onBack = {
            if (!isLeaving) {
                isLeaving = true
                scope.launch {
                    viewModel.saveOnExit()
                    onBack()
                }
            }
        },
        onProgressChanged = viewModel::onProgressChanged,
        renderPage = viewModel::renderPage,
        extraActions = { listState ->
            if (state.content != null) {
                BookmarkMenu(
                    bookmarks = state.bookmarks,
                    currentPage = state.currentPage,
                    error = state.bookmarkError,
                    listState = listState,
                    onMarkPage = viewModel::onBookmarkFormOpen,
                )
            }
        },
    )

    state.bookmarkForm?.let { form ->
        BookmarkDialog(
            form = form,
            onNoteChange = viewModel::onBookmarkNoteChange,
            onSave = viewModel::onBookmarkSave,
            onDelete = viewModel::onBookmarkDelete,
            onDismiss = viewModel::onBookmarkFormDismiss,
        )
    }
}

/** Jump targets for the table-of-contents menu, as LazyColumn item indices. */
private class Sections(val titles: List<String>, val starts: List<Int>) {
    fun indexAt(itemIndex: Int): Int = starts.indexOfLast { it <= itemIndex }
}

/** A document flattened for LazyColumn: every paragraph is one item. */
private class FlatDocument(chapters: List<Chapter>) {
    val paragraphs: List<Paragraph> = chapters.flatMap { it.paragraphs }
    val sections = Sections(
        titles = chapters.map { it.title },
        starts = chapters.runningFold(0) { start, chapter -> start + chapter.paragraphs.size }.dropLast(1),
    )
}

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onProgressChanged: (Float) -> Unit,
    renderPage: suspend (pageIndex: Int, widthPx: Int) -> ImageBitmap,
    extraActions: @Composable (LazyListState) -> Unit = {},
) {
    val content = state.content
    val document = remember(content) { (content as? BookContent.Document)?.let { FlatDocument(it.chapters) } }
    val pages = content as? BookContent.Pages
    val sections = remember(content) {
        document?.sections ?: pages?.let { Sections(it.outline.map { e -> e.title }, it.outline.map { e -> e.pageIndex }) }
    }
    val listState = rememberLazyListState()
    val currentItem by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    val subtitle = when {
        pages != null -> "Halaman ${currentItem + 1} dari ${pages.pages.size}"
        sections != null -> sections.titles.getOrNull(sections.indexAt(currentItem))
        else -> null
    }

    Column(Modifier.fillMaxSize().background(Archive.Canvas)) {
        // A slim bar: the page is the subject here, so the header stays out of its way.
        Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp)) {
            BackButton(onBack, Modifier.align(Alignment.CenterStart))
            Column(
                Modifier.align(Alignment.Center).widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { MetaLine(it, color = Archive.Muted) }
            }
            Row(
                Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                extraActions(listState)
                if (sections != null && sections.titles.size > 1) {
                    SectionMenu(sections, sections.indexAt(currentItem), listState)
                }
            }
        }
        HorizontalDivider(color = Archive.Line)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.error != null -> Text(
                    state.error,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 520.dp),
                )
                state.downloadProgress != null -> Column(
                    modifier = Modifier.align(Alignment.Center).widthIn(max = 320.dp).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Mengunduh buku...", style = MaterialTheme.typography.headlineSmall)
                    LinearProgressIndicator(
                        progress = { state.downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = Archive.Ink,
                        trackColor = Archive.Line,
                    )
                }
                content == null -> Text("Membuka buku...", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.Center))
                document != null -> {
                    TrackProgress(listState, document.paragraphs.lastIndex, state.initialProgress, onProgressChanged)
                    DocumentContent(document, listState)
                }
                pages != null -> {
                    TrackProgress(listState, pages.pages.lastIndex, state.initialProgress, onProgressChanged)
                    PagesContent(pages.pages, listState, renderPage)
                }
                else -> Text("Format ini belum didukung oleh reader.", Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun SectionMenu(sections: Sections, currentSection: Int, listState: LazyListState) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box {
        ArchiveLink("Daftar isi", { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sections.titles.forEachIndexed { index, title ->
                DropdownMenuItem(
                    text = {
                        Text(
                            title,
                            style = if (index == currentSection) {
                                MaterialTheme.typography.bodyMedium.copy(color = Archive.Accent)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        scope.launch { listState.scrollToItem(sections.starts[index]) }
                    },
                )
            }
        }
    }
}

/** Restores the saved position once, then reports progress as the reader scrolls. */
@Composable
private fun TrackProgress(
    listState: LazyListState,
    lastIndex: Int,
    initialProgress: Float,
    onProgressChanged: (Float) -> Unit,
) {
    LaunchedEffect(lastIndex) {
        if (initialProgress > 0f && lastIndex > 0) {
            listState.scrollToItem((lastIndex * initialProgress).roundToInt())
        }
        snapshotFlow {
            val atEnd = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == lastIndex
            if (atEnd || lastIndex <= 0) 1f else listState.firstVisibleItemIndex.toFloat() / lastIndex
        }.collect(onProgressChanged)
    }
}

@Composable
private fun DocumentContent(document: FlatDocument, listState: LazyListState) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp),
    ) {
        items(document.paragraphs) { paragraph ->
            Text(
                text = paragraph.text,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = if (paragraph.isHeading) 16.dp else 8.dp),
                style = if (paragraph.isHeading) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 30.sp)
                },
            )
        }
    }
}

@Composable
private fun PagesContent(
    pages: List<PageSize>,
    listState: LazyListState,
    renderPage: suspend (pageIndex: Int, widthPx: Int) -> ImageBitmap,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageWidth = minOf(maxWidth - 32.dp, 900.dp)
        // Round up to 100px steps so resizing the window doesn't re-render on every pixel.
        val widthPx = with(LocalDensity.current) { pageWidth.roundToPx() }.let { (it + 99) / 100 * 100 }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(pages) { index, size ->
                PdfPage(index, size, pageWidth, widthPx, renderPage)
            }
        }
    }
}

private sealed interface PageImage {
    data object Loading : PageImage
    data class Ready(val bitmap: ImageBitmap) : PageImage
    data object Failed : PageImage
}

@Composable
private fun PdfPage(
    index: Int,
    size: PageSize,
    width: Dp,
    widthPx: Int,
    renderPage: suspend (pageIndex: Int, widthPx: Int) -> ImageBitmap,
) {
    // Keeps the previous bitmap on screen while a resized one renders.
    val image by produceState<PageImage>(PageImage.Loading, index, widthPx) {
        value = try {
            PageImage.Ready(renderPage(index, widthPx))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PageImage.Failed
        }
    }

    Box(
        Modifier.width(width).aspectRatio(size.width / size.height).background(Color.White).border(1.dp, Archive.Line),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = image) {
            PageImage.Loading -> CircularProgressIndicator(color = Archive.Line, strokeWidth = 2.dp)
            PageImage.Failed -> Text("Gagal memuat halaman ${index + 1}.", color = Color.DarkGray)
            is PageImage.Ready -> Image(current.bitmap, contentDescription = "Halaman ${index + 1}", Modifier.fillMaxSize())
        }
    }
}
