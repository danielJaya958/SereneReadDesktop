package com.sereneread.project.feature.reader

import com.sereneread.project.feature.reader.data.BookmarkRepositoryImpl
import com.sereneread.project.feature.reader.domain.BookmarkRepository
import com.sereneread.project.feature.reader.presentation.ReaderViewModel
import com.sereneread.project.feature.reader.presentation.RemoteReaderViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val readerModule = module {
    single<BookmarkRepository> { BookmarkRepositoryImpl(get()) }
    viewModel { (bookId: Long) -> ReaderViewModel(bookId, get(), get(), get()) }
    viewModel { (bookId: Long) -> RemoteReaderViewModel(bookId, get(), get(), get(), get(), get(), get()) }
}
