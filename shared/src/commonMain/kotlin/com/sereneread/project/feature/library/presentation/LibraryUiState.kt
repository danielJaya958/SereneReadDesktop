package com.sereneread.project.feature.library.presentation

import com.sereneread.project.feature.library.domain.BookSort
import com.sereneread.project.feature.library.domain.RemoteBook

data class LibraryUiState(
    val message: String? = null,
    val query: String = "",
    val sort: BookSort = BookSort.NEWEST,
    val filter: ShelfFilter = ShelfFilter.ALL,
    /** The book opened out on the shelf. Kept here so it survives a visit to the detail page. */
    val activeKey: String? = null,
    val remoteBooks: List<RemoteBook> = emptyList(),
    val remotePage: Int = 0,
    val hasMoreRemote: Boolean = false,
    val isLoadingRemote: Boolean = true,
    val remoteError: String? = null,
    val isOffline: Boolean = false,
    /** Every book saved on this computer, which is also what the Downloaded filter shows. */
    val downloaded: List<RemoteBook> = emptyList(),
    /** Progress pages and reading sessions still waiting for the server. */
    val pendingSync: Long = 0,
    val uploadForm: UploadForm? = null,
)

/** Which books the shelf shows. Favorites are left out: the server cannot filter them across pages. */
enum class ShelfFilter { ALL, DOWNLOADED }

data class UploadForm(
    val filePath: String,
    val title: String,
    val author: String,
    val year: String = "",
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !isUploading && title.isNotBlank() && author.isNotBlank() && year.toIntOrNull() != null
}
