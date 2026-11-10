package com.sereneread.project.core.platform

import java.awt.Desktop
import java.io.File

/** Selects the file in Explorer or Finder; on other systems it opens the containing folder. */
class DesktopFileLocations : FileLocations {

    override fun reveal(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        val os = System.getProperty("os.name").lowercase()
        return try {
            when {
                os.contains("win") -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                os.contains("mac") -> ProcessBuilder("open", "-R", file.absolutePath).start()
                else -> Desktop.getDesktop().open(file.parentFile)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
