package com.sereneread.project.feature.library

import com.sereneread.project.core.di.IoDispatcher
import com.sereneread.project.feature.library.data.BookRepositoryImpl
import com.sereneread.project.feature.library.domain.BookRepository
import com.sereneread.project.feature.library.domain.ImportBookUseCase
import com.sereneread.project.feature.library.presentation.LibraryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val libraryModule = module {
    single<BookRepository> { BookRepositoryImpl(get(), get(IoDispatcher)) }
    factory { ImportBookUseCase(get(), get()) }
    viewModelOf(::LibraryViewModel)
}
