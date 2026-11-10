package com.sereneread.project.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sereneread.project.core.session.SessionManager
import com.sereneread.project.core.ui.archive.LocalNavAnimatedScope
import com.sereneread.project.core.ui.archive.LocalSharedTransitionScope
import com.sereneread.project.core.session.SessionState
import com.sereneread.project.feature.auth.domain.SignOutUseCase
import com.sereneread.project.feature.auth.presentation.AuthRoute
import com.sereneread.project.feature.library.presentation.BookDetailRoute
import com.sereneread.project.feature.library.presentation.LibraryRoute
import com.sereneread.project.feature.offline.data.OfflineSync
import com.sereneread.project.feature.reader.presentation.RemoteReaderRoute
import com.sereneread.project.feature.stats.presentation.StatsRoute
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** The session state decides what is shown, so a 401 anywhere returns the user to sign-in. */
@Composable
fun AppNavHost(
    sessionManager: SessionManager = koinInject(),
    signOut: SignOutUseCase = koinInject(),
) {
    val session by sessionManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(sessionManager) { sessionManager.restore() }

    when (val current = session) {
        SessionState.Loading -> Box(Modifier.fillMaxSize())
        is SessionState.Unauthenticated -> AuthRoute(sessionExpired = current.expired)
        SessionState.Authenticated -> MainNavHost(onSignOut = { scope.launch { signOut() } })
    }
}

/** Destinations fade into each other; the shared cover moves on top, so no screen hard-cuts. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainNavHost(onSignOut: () -> Unit, sync: OfflineSync = koinInject()) {
    val navController = rememberNavController()
    // Sends reading done offline as soon as the server is reachable again, for as long as the user is signed in.
    LaunchedEffect(sync) { sync.keepSending() }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(navController = navController, startDestination = LibraryDestination) {
                screen<LibraryDestination> {
                    LibraryRoute(
                        onOpenRemoteBook = { navController.navigate(BookDetailDestination(it)) },
                        onOpenStats = { navController.navigate(StatsDestination) },
                        onSignOut = onSignOut,
                    )
                }
                screen<StatsDestination> {
                    StatsRoute(onBack = { navController.popBackStack() })
                }
                screen<BookDetailDestination> { entry ->
                    BookDetailRoute(
                        bookId = entry.toRoute<BookDetailDestination>().bookId,
                        onBack = { navController.popBackStack() },
                        onRead = { navController.navigate(RemoteReaderDestination(it)) },
                    )
                }
                screen<RemoteReaderDestination> { entry ->
                    RemoteReaderRoute(
                        bookId = entry.toRoute<RemoteReaderDestination>().bookId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    composable<T>(
        enterTransition = { fadeIn(tween(320, delayMillis = 120)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(320, delayMillis = 120)) },
        popExitTransition = { fadeOut(tween(200)) },
    ) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}
