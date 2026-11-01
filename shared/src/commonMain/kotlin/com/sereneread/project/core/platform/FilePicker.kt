package com.sereneread.project.core.platform

/** Opens a native file dialog. Returns the absolute path, or null when the user cancels. */
fun interface FilePicker {
    suspend fun pickFile(allowedExtensions: Set<String>): String?
}
