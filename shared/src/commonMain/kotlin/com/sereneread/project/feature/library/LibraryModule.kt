package com.sereneread.project.feature.library

import com.sereneread.project.core.di.IoDispatcher
import com.sereneread.project.feature.library.data.BookCoversImpl
import com.sereneread.project.feature.library.presentation.BookCovers
import com.sereneread.project.feature.library.data.RemoteBookRepositoryImpl
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import com.sereneread.project.feature.library.presentation.BookDetailViewModel
import com.sereneread.project.feature.library.presentation.LibraryViewModel
import com.sereneread.project.feature.offline.data.OfflineRemoteBookRepository
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val libraryModule = module {
    single<RemoteBookRepository> { OfflineRemoteBookRepository(RemoteBookRepositoryImpl(get()), get(), get(), get()) }
    single<BookCovers> { BookCoversImpl(get(), get(IoDispatcher)) }
    viewModelOf(::LibraryViewModel)
    viewModel { (bookId: Long) -> BookDetailViewModel(bookId, get(), get(), get()) }
}
