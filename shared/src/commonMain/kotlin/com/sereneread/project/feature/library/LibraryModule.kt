package com.sereneread.project.feature.library

import com.sereneread.project.core.di.IoDispatcher
import com.sereneread.project.feature.library.data.BookCoversImpl
import com.sereneread.project.feature.library.data.BookRepositoryImpl
import com.sereneread.project.feature.library.presentation.BookCovers
import com.sereneread.project.feature.library.data.RemoteBookRepositoryImpl
import com.sereneread.project.feature.library.domain.BookRepository
import com.sereneread.project.feature.library.domain.ImportBookUseCase
import com.sereneread.project.feature.library.domain.RemoteBookRepository
import com.sereneread.project.feature.library.presentation.BookDetailViewModel
import com.sereneread.project.feature.library.presentation.LibraryViewModel
import com.sereneread.project.feature.offline.data.OfflineRemoteBookRepository
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val libraryModule = module {
    single<BookRepository> { BookRepositoryImpl(get(), get(IoDispatcher)) }
    single<RemoteBookRepository> { OfflineRemoteBookRepository(RemoteBookRepositoryImpl(get()), get(), get()) }
    single<BookCovers> { BookCoversImpl(get(), get(IoDispatcher)) }
    factory { ImportBookUseCase(get(), get()) }
    viewModelOf(::LibraryViewModel)
    viewModel { (bookId: Long) -> BookDetailViewModel(bookId, get(), get()) }
}
