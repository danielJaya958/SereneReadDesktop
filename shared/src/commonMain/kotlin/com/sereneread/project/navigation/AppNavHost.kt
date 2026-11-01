package com.sereneread.project.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sereneread.project.feature.library.presentation.LibraryRoute
import com.sereneread.project.feature.reader.presentation.ReaderRoute

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LibraryDestination) {
        composable<LibraryDestination> {
            LibraryRoute(onOpenBook = { navController.navigate(ReaderDestination(it)) })
        }
        composable<ReaderDestination> { entry ->
            ReaderRoute(
                bookId = entry.toRoute<ReaderDestination>().bookId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
