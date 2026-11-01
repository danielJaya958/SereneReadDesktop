package com.sereneread.project.core.session

class FakeTokenStore(var token: String? = null) : TokenStore {
    var failOnRead = false
    var failOnSave = false
    var failOnClear = false

    override suspend fun read(): String? {
        if (failOnRead) error("storage unavailable")
        return token
    }

    override suspend fun save(token: String) {
        if (failOnSave) error("storage unavailable")
        this.token = token
    }

    override suspend fun clear() {
        if (failOnClear) error("storage unavailable")
        token = null
    }
}
