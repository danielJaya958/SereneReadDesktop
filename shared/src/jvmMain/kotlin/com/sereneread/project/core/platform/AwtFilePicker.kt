package com.sereneread.project.core.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class AwtFilePicker : FilePicker {
    override suspend fun pickFile(allowedExtensions: Set<String>): String? = withContext(Dispatchers.Main) {
        val dialog = FileDialog(null as Frame?, "Pilih buku", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.').lowercase() in allowedExtensions }
            // Windows ignores FilenameFilter; a wildcard pattern filters there instead.
            file = allowedExtensions.joinToString(";") { "*.$it" }
            isVisible = true
        }
        val name = dialog.file ?: return@withContext null
        File(dialog.directory, name).absolutePath
    }
}
