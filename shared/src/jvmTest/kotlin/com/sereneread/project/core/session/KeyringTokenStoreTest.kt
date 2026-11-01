package com.sereneread.project.core.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Uses the real OS credential store, under a test-only service name that it removes again. */
class KeyringTokenStoreTest {

    private val store = KeyringTokenStore(Dispatchers.IO, service = "SereneRead-Test", account = "test-token")

    @Test
    fun savesReadsAndClearsToken() = runTest {
        store.clear()
        assertNull(store.read())

        store.save("first")
        store.save("second")
        assertEquals("second", store.read())

        store.clear()
        assertNull(store.read())
    }
}
