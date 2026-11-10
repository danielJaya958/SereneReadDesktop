package com.sereneread.project.core.platform

/** Shows a file in the system file manager. */
fun interface FileLocations {
    /** Returns false when the file manager could not be opened. */
    fun reveal(path: String): Boolean
}
