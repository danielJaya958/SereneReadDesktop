package com.sereneread.project.core.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionManagerTest {

    private val store = FakeTokenStore()
    private val manager = SessionManager(store)

    @Test
    fun startsLoadingUntilRestored() {
        assertEquals(SessionState.Loading, manager.state.value)
    }

    @Test
    fun restoresStoredToken() = runTest {
        store.token = "saved"

        manager.restore()

        assertEquals(SessionState.Authenticated, manager.state.value)
        assertEquals("saved", manager.currentToken())
    }

    @Test
    fun restoreWithoutTokenIsUnauthenticated() = runTest {
        manager.restore()

        assertEquals(SessionState.Unauthenticated(), manager.state.value)
    }

    @Test
    fun unreadableStorageCountsAsSignedOut() = runTest {
        store.failOnRead = true

        manager.restore()

        assertEquals(SessionState.Unauthenticated(), manager.state.value)
    }

    @Test
    fun signInPersistsToken() = runTest {
        manager.signIn("new")

        assertEquals("new", store.token)
        assertEquals(SessionState.Authenticated, manager.state.value)
    }

    @Test
    fun signOutClearsMemoryAndStorage() = runTest {
        manager.signIn("new")

        manager.signOut()

        assertNull(store.token)
        assertNull(manager.currentToken())
        assertEquals(SessionState.Unauthenticated(), manager.state.value)
    }

    @Test
    fun expireMarksSessionExpired() = runTest {
        manager.signIn("old")

        manager.expire("old")

        assertNull(store.token)
        assertEquals(SessionState.Unauthenticated(expired = true), manager.state.value)
    }

    @Test
    fun expireIgnoresStaleToken() = runTest {
        manager.signIn("new")

        manager.expire("old")

        assertEquals("new", manager.currentToken())
        assertEquals(SessionState.Authenticated, manager.state.value)
    }

    @Test
    fun storageFailureStillEndsSessionInMemory() = runTest {
        manager.signIn("old")
        store.failOnClear = true

        manager.signOut()

        assertNull(manager.currentToken())
        assertEquals(SessionState.Unauthenticated(), manager.state.value)
    }
}
