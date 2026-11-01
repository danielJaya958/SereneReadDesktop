package com.sereneread.project.feature.auth

import com.sereneread.project.core.network.AppError
import com.sereneread.project.core.network.AppResult
import com.sereneread.project.feature.auth.domain.AuthRepository
import com.sereneread.project.feature.auth.presentation.AuthMode
import com.sereneread.project.feature.auth.presentation.AuthUiState
import com.sereneread.project.feature.auth.presentation.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private class FakeAuthRepository : AuthRepository {
        var loginResult: AppResult<Unit> = AppResult.Success(Unit)
        var registerResult: AppResult<Unit> = AppResult.Success(Unit)
        val calls = mutableListOf<String>()

        override suspend fun login(email: String, password: String): AppResult<Unit> {
            calls += "login:$email"
            return loginResult
        }

        override suspend fun register(username: String, email: String, password: String): AppResult<Unit> {
            calls += "register:$username:$email"
            return registerResult
        }
    }

    private val repository = FakeAuthRepository()
    private lateinit var viewModel: AuthViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AuthViewModel(repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fillLogin() {
        viewModel.onEmailChange(" a@b.c ")
        viewModel.onPasswordChange("secret")
    }

    private fun fillRegister() {
        viewModel.onModeChange(AuthMode.Register)
        viewModel.onUsernameChange("jay")
        fillLogin()
    }

    @Test
    fun submitIsDisabledUntilFormIsComplete() {
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onSubmit()

        assertEquals(emptyList(), repository.calls)
    }

    @Test
    fun successfulLoginTrimsEmailAndClearsPassword() {
        fillLogin()

        viewModel.onSubmit()

        assertEquals(listOf("login:a@b.c"), repository.calls)
        assertEquals(AuthUiState(), viewModel.state.value)
    }

    @Test
    fun failedLoginShowsServerMessage() {
        repository.loginResult = AppResult.Failure(AppError.Unauthorized("email atau password salah"))
        fillLogin()

        viewModel.onSubmit()

        assertEquals("email atau password salah", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun registerSignsInAfterwards() {
        fillRegister()

        viewModel.onSubmit()

        assertEquals(listOf("register:jay:a@b.c", "login:a@b.c"), repository.calls)
        assertEquals(AuthUiState(), viewModel.state.value)
    }

    @Test
    fun failedRegisterDoesNotSignIn() {
        repository.registerResult = AppResult.Failure(AppError.Conflict("Email sudah terdaftar"))
        fillRegister()

        viewModel.onSubmit()

        assertEquals(listOf("register:jay:a@b.c"), repository.calls)
        assertEquals(AuthMode.Register, viewModel.state.value.mode)
        assertEquals("Email sudah terdaftar", viewModel.state.value.error)
    }

    @Test
    fun failedLoginAfterRegisterMovesToLoginMode() {
        repository.loginResult = AppResult.Failure(AppError.Server(500, "gagal"))
        fillRegister()

        viewModel.onSubmit()

        assertEquals(AuthMode.Login, viewModel.state.value.mode)
        assertEquals("gagal", viewModel.state.value.error)
    }
}
