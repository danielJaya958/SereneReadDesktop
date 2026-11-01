package com.sereneread.project.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.core.session.SessionState
import com.sereneread.project.feature.auth.presentation.AuthRoute
import com.sereneread.project.feature.library.presentation.LibraryRoute
import com.sereneread.project.feature.reader.presentation.ReaderRoute
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The session state decides what is shown, so a 401 anywhere returns the user to sign-in. */
@Composable
fun AppNavHost(sessionManager: SessionManager = koinInject()) {
    val session by sessionManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(sessionManager) { sessionManager.restore() }

    when (val current = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        is SessionState.Unauthenticated -> AuthRoute(sessionExpired = current.expired)
        SessionState.Authenticated -> MainNavHost(onSignOut = { scope.launch { sessionManager.signOut() } })
    }
}

@Composable
private fun MainNavHost(onSignOut: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = LibraryDestination) {
        composable<LibraryDestination> {
            LibraryRoute(
                onOpenBook = { navController.navigate(ReaderDestination(it)) },
                onSignOut = onSignOut,
            )
        }
        composable<ReaderDestination> { entry ->
            ReaderRoute(
                bookId = entry.toRoute<ReaderDestination>().bookId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
