package com.sereneread.project.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryDestination

@Serializable
data class ReaderDestination(val bookId: Long)
