package com.sereneread.project.feature.library.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sereneread.project.core.ui.theme.SereneReadTheme
import com.sereneread.project.feature.library.domain.RemoteBook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ShelfScrollTest {

    private val books = (1..6).map { RemoteBook(it.toLong(), "Buku $it", "Penulis", 2020, 0, 100, false) }
    private fun label(index: Int) = "Buku ${index + 1}, Penulis"

    @Test
    fun wheelStepsOneBookAndKeepsItCentredAtBothEnds() = runComposeUiTest {
        var state by mutableStateOf(LibraryUiState(remoteBooks = books, isLoading = false, isLoadingRemote = false))
        setContent {
            SereneReadTheme {
                LibraryScreen(
                    state = state,
                    loadCover = { null },
                    onOpen = {},
                    onActiveChange = { state = state.copy(activeKey = it) },
                    onFilterChange = {},
                    onQueryChange = {},
                    onSortChange = {},
                    onLoadMore = {},
                    onRetryRemote = {},
                    onImportClick = {},
                    onUploadClick = {},
                    onDeleteLocal = {},
                    onMessageShown = {},
                    onOpenStats = {},
                    onSignOut = {},
                )
            }
        }

        fun assertCentred(index: Int) {
            waitForIdle()
            val root = onRoot().getBoundsInRoot()
            val book = onNodeWithContentDescription(label(index)).getBoundsInRoot()
            val diff: Dp = ((book.left + book.right) - (root.left + root.right)) / 2
            val offCentre = if (diff < 0.dp) -diff else diff
            assertTrue(offCentre < 2.dp, "book ${index + 1} is ${offCentre} off centre")
            assertTrue(book.right - book.left > 200.dp, "book ${index + 1} is not open")
        }

        assertCentred(0)
        for (index in 1..books.lastIndex) {
            onNodeWithContentDescription(label(index - 1)).performMouseInput { moveTo(center); scroll(1f) }
            waitForIdle()
            assertEquals("remote-${index + 1}", state.activeKey)
            assertCentred(index)
        }
        // Scrolling past the last book stays on it instead of bouncing.
        onNodeWithContentDescription(label(books.lastIndex)).performMouseInput { moveTo(center); scroll(1f) }
            waitForIdle()
        assertCentred(books.lastIndex)

        for (index in books.lastIndex - 1 downTo 0) {
            onNodeWithContentDescription(label(index + 1)).performMouseInput { moveTo(center); scroll(-1f) }
            waitForIdle()
            assertEquals("remote-${index + 1}", state.activeKey)
            assertCentred(index)
        }
    }
}
