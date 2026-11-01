package com.sereneread.project.feature.auth

import com.sereneread.project.feature.auth.data.AuthRepositoryImpl
import com.sereneread.project.feature.auth.domain.AuthRepository
import com.sereneread.project.feature.auth.presentation.AuthViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    viewModelOf(::AuthViewModel)
}
