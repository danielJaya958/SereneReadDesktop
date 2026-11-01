package com.sereneread.project.core.di

import com.sereneread.project.feature.library.libraryModule
import com.sereneread.project.feature.reader.readerModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/** Platform bindings: database driver, file picker, file readers. */
expect val platformModule: Module

fun initKoin(config: KoinAppDeclaration = {}) {
    startKoin {
        config()
        modules(platformModule, coreModule, libraryModule, readerModule)
    }
}
