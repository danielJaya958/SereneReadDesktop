package com.sereneread.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sereneread.project.core.ui.theme.Archive
import com.sereneread.project.core.ui.theme.SereneReadTheme
import com.sereneread.project.navigation.AppNavHost

@Composable
fun App() {
    SereneReadTheme {
        Box(Modifier.fillMaxSize().background(Archive.Canvas)) { AppNavHost() }
    }
}
