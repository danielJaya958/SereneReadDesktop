package com.sereneread.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sereneread.project.core.di.initKoin
import com.sereneread.project.core.lifecycle.ExitHooks
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

fun main() {
    initKoin()
    application {
        val exitHooks = remember { KoinPlatform.getKoin().get<ExitHooks>() }
        val scope = rememberCoroutineScope()
        var isClosing by remember { mutableStateOf(false) }
        Window(
            // Saves what the open screen still holds before the process ends.
            onCloseRequest = {
                if (!isClosing) {
                    isClosing = true
                    scope.launch {
                        exitHooks.runAll()
                        exitApplication()
                    }
                }
            },
            title = "SereneRead",
            state = rememberWindowState(placement = WindowPlacement.Maximized),
        ) {
            App()
        }
    }
}
