package com.sereneread.project.feature.auth.domain

import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.feature.library.domain.RemoteBookFiles
import com.sereneread.project.feature.offline.data.OfflineStore

/** Removes the account's books from this computer. An expired session keeps them for the next sign-in. */
class SignOutUseCase(
    private val session: SessionManager,
    private val store: OfflineStore,
    private val files: RemoteBookFiles,
) {
    suspend operator fun invoke() {
        session.signOut()
        store.clear()
        files.deleteAll()
    }
}
