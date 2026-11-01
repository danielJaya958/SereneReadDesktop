package com.sereneread.project.core.session

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Stores the token in the OS credential store (Windows Credential Manager, macOS Keychain,
 * or Secret Service on Linux). java-keyring throws PasswordAccessException for a missing entry.
 */
class KeyringTokenStore(
    private val ioDispatcher: CoroutineDispatcher,
    private val service: String = "SereneRead",
    private val account: String = "access-token",
) : TokenStore {

    override suspend fun read(): String? = withContext(ioDispatcher) {
        Keyring.create().use { keyring ->
            try {
                keyring.getPassword(service, account)
            } catch (e: PasswordAccessException) {
                null
            }
        }
    }

    override suspend fun save(token: String) = withContext(ioDispatcher) {
        Keyring.create().use { it.setPassword(service, account, token) }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        Keyring.create().use { keyring ->
            try {
                keyring.deletePassword(service, account)
            } catch (e: PasswordAccessException) {
                // Nothing stored.
            }
        }
    }
}
