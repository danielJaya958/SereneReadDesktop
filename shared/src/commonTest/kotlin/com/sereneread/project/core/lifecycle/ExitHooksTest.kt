package com.sereneread.project.core.lifecycle

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ExitHooksTest {

    private val hooks = ExitHooks(timeoutMillis = 1_000)
    private val calls = mutableListOf<String>()

    @Test
    fun runsEveryHookOnce() = runTest {
        hooks.add { calls += "a" }
        hooks.add { calls += "b" }

        hooks.runAll()
        hooks.runAll()

        assertEquals(listOf("a", "b"), calls)
    }

    @Test
    fun removedHookDoesNotRun() = runTest {
        val remove = hooks.add { calls += "a" }
        remove()

        hooks.runAll()

        assertEquals(emptyList(), calls)
    }

    @Test
    fun failingHookDoesNotStopOthers() = runTest {
        hooks.add { error("broken") }
        hooks.add { calls += "b" }

        hooks.runAll()

        assertEquals(listOf("b"), calls)
    }

    @Test
    fun slowHookIsCutOffByTimeout() = runTest {
        hooks.add { awaitCancellation() }

        hooks.runAll()

        assertEquals(1_000, testScheduler.currentTime)
    }
}
