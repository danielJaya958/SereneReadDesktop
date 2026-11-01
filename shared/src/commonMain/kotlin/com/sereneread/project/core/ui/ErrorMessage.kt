package com.sereneread.project.core.ui

import com.sereneread.project.core.network.AppError

/** Prefers the server's own message, which is already written for the user. */
fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "Tidak dapat terhubung ke server. Periksa koneksi Anda."
    is AppError.Unauthorized -> message ?: "Sesi tidak valid. Silakan masuk lagi."
    is AppError.Forbidden -> message ?: "Anda tidak memiliki akses."
    is AppError.NotFound -> message ?: "Data tidak ditemukan."
    is AppError.Conflict -> message ?: "Data sudah ada."
    is AppError.Validation -> message ?: "Data tidak valid."
    is AppError.Server -> message ?: "Server sedang bermasalah. Coba lagi nanti."
    is AppError.Unexpected -> "Terjadi kesalahan yang tidak terduga."
}
