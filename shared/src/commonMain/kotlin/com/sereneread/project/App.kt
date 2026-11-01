package com.sereneread.project

import androidx.compose.runtime.Composable
import com.sereneread.project.core.ui.theme.SereneReadTheme
import com.sereneread.project.navigation.AppNavHost

@Composable
fun App() {
    SereneReadTheme {
        AppNavHost()
    }
}
