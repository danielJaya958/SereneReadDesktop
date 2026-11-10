package com.sereneread.project.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryDestination

@Serializable
data object StatsDestination

@Serializable
data class BookDetailDestination(val bookId: Long)

@Serializable
data class RemoteReaderDestination(val bookId: Long)
