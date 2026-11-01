package com.sereneread.project.feature.reader

import com.sereneread.project.feature.reader.presentation.ReaderViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val readerModule = module {
    viewModel { (bookId: Long) -> ReaderViewModel(bookId, get(), get(), get()) }
}
