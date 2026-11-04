package com.sereneread.project.core.lifecycle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Work that must run before the app process ends, such as saving the reader position.
 * Screens register while they are open, because closing the window skips ViewModel.onCleared.
 */
class ExitHooks(private val timeoutMillis: Long = 3_000) {

    private val hooks = MutableStateFlow<List<suspend () -> Unit>>(emptyList())

    /** Returns a function that removes the hook again. */
    fun add(hook: suspend () -> Unit): () -> Unit {
        hooks.update { it + hook }
        return { hooks.update { it - hook } }
    }

    /** Runs every hook once. A failing or slow hook never stops the app from closing. */
    suspend fun runAll() {
        val pending = hooks.getAndUpdate { emptyList() }
        withTimeoutOrNull(timeoutMillis) {
            for (hook in pending) {
                try {
                    hook()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The next hook still deserves its chance to save.
                }
            }
        }
    }
}
