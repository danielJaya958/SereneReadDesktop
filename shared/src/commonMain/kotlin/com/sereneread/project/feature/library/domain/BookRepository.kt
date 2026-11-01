package com.sereneread.project.feature.library.domain

import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBooks(): Flow<List<Book>>
    suspend fun getBook(id: Long): Book?
    suspend fun findByPath(filePath: String): Book?
    suspend fun addBook(title: String, author: String?, filePath: String, format: BookFormat, addedAt: Long): Long
    suspend fun markOpened(id: Long, openedAt: Long)
    suspend fun updateProgress(id: Long, progress: Float)
    suspend fun deleteBook(id: Long)
}
