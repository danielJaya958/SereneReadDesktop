package com.sereneread.project.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseMigrationTest {

    private val dbFile = File(Files.createTempDirectory("db-test").toFile(), "test.db")
    private val url = "jdbc:sqlite:${dbFile.absolutePath}"

    @AfterTest
    fun cleanUp() {
        dbFile.parentFile.deleteRecursively()
    }

    @Test
    fun migratesVersion1DatabaseAndKeepsExistingRows() {
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use {
                it.executeUpdate(
                    """
                    CREATE TABLE BookEntity (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        filePath TEXT NOT NULL UNIQUE,
                        format TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        lastOpenedAt INTEGER,
                        progress REAL NOT NULL DEFAULT 0.0
                    )
                    """.trimIndent(),
                )
                it.executeUpdate("INSERT INTO BookEntity(title, filePath, format, addedAt) VALUES ('Lama', '/a.txt', 'TXT', 1)")
                it.executeUpdate("PRAGMA user_version = 1")
            }
        }

        val driver = JdbcSqliteDriver(url, Properties(), SereneReadDatabase.Schema)
        val queries = SereneReadDatabase(driver).bookQueries
        queries.insert("Baru", "Penulis", "/b.epub", "EPUB", 2)

        val books = queries.selectAll().executeAsList().sortedBy { it.id }
        assertEquals(listOf("Lama" to null, "Baru" to "Penulis"), books.map { it.title to it.author })
        assertEquals(listOf("/a.txt", "/b.epub"), books.map { it.filePath })
        driver.close()
    }
}
