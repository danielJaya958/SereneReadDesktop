package com.sereneread.project.core.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sereneread.project.core.database.SereneReadDatabase
import com.sereneread.project.core.platform.AwtFilePicker
import com.sereneread.project.core.platform.FilePicker
import com.sereneread.project.core.session.KeyringTokenStore
import com.sereneread.project.core.session.TokenStore
import com.sereneread.project.feature.library.data.FileBookMetadataReader
import com.sereneread.project.feature.library.domain.BookMetadataReader
import com.sereneread.project.feature.reader.data.EpubParser
import com.sereneread.project.feature.reader.data.FileBookContentReader
import com.sereneread.project.feature.reader.data.PdfPageRenderer
import com.sereneread.project.feature.reader.data.PdfParser
import com.sereneread.project.feature.reader.presentation.PageRenderer
import com.sereneread.project.feature.reader.domain.BookContentReader
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.util.Properties

actual val platformModule: Module = module {
    single<SqlDriver> {
        val dir = File(System.getProperty("user.home"), ".sereneread").apply { mkdirs() }
        JdbcSqliteDriver(
            url = "jdbc:sqlite:${File(dir, "sereneread.db").absolutePath}",
            properties = Properties(),
            schema = SereneReadDatabase.Schema,
        )
    }
    single<HttpClientEngine> { OkHttp.create() }
    single<TokenStore> { KeyringTokenStore(get(IoDispatcher)) }
    single<FilePicker> { AwtFilePicker() }
    single { EpubParser() }
    single { PdfParser() }
    single<PageRenderer> { PdfPageRenderer(get(IoDispatcher)) }
    single<BookContentReader> { FileBookContentReader(get(), get(), get(IoDispatcher)) }
    single<BookMetadataReader> { FileBookMetadataReader(get(), get(), get(IoDispatcher)) }
}
