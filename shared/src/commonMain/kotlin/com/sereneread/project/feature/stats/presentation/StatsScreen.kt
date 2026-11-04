package com.sereneread.project.feature.stats.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sereneread.project.feature.stats.domain.DailyStat
import com.sereneread.project.feature.stats.domain.StatsSummary
import org.koin.compose.viewmodel.koinViewModel

private val DAY_NAMES = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")

@Composable
fun StatsRoute(onBack: () -> Unit, viewModel: StatsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.load() }
    StatsScreen(state, onBack, onRetry = viewModel::load)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: StatsUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistik") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Kembali") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.summary != null -> StatsContent(state, state.summary)
                else -> Column(Modifier.align(Alignment.Center)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Coba lagi") }
                }
            }
        }
    }
}

@Composable
private fun StatsContent(state: StatsUiState, summary: StatsSummary) {
    Column(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Ringkasan")
        Line("Hari ini", "${summary.todayMinutes} menit, ${summary.todayPages} halaman")
        Line(
            "Minggu ini",
            "${summary.weekMinutes} menit, ${summary.weekPages} halaman, ${summary.weekActiveDays} hari aktif",
        )
        Line(
            "Sepanjang waktu",
            "${summary.totalMinutes} menit, ${summary.totalPages} halaman, ${summary.totalSessions} sesi",
        )
        Line("Runtutan", "${summary.currentStreakDays} hari, terpanjang ${summary.longestStreakDays} hari")
        Line(
            "Buku selesai",
            "${summary.booksFinishedThisYear} tahun ini, ${summary.booksFinishedAllTime} total",
        )

        SectionTitle("7 hari terakhir")
        state.daily.forEach { DayLine(it) }

        SectionTitle("Buku paling lama dibaca")
        if (state.books.isEmpty()) Text("Belum ada sesi membaca.")
        state.books.forEach { Line(it.title, "${it.minutes} menit, ${it.pages} halaman") }
    }
}

@Composable
private fun DayLine(day: DailyStat) {
    Line("${DAY_NAMES[day.date.dayOfWeek.ordinal]}, ${day.date}", "${day.minutes} menit, ${day.pages} halaman")
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
