package com.sereneread.project

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sereneread.project.core.di.initKoin

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "SereneRead",
            state = rememberWindowState(placement = WindowPlacement.Maximized),
        ) {
            App()
        }
    }
}
