package com.sereneread.project.feature.stats.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sereneread.project.core.ui.archive.ArchiveButton
import com.sereneread.project.core.ui.archive.ArchiveHeader
import com.sereneread.project.core.ui.archive.BackButton
import com.sereneread.project.core.ui.archive.MetaLine
import com.sereneread.project.core.ui.theme.Archive
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

@Composable
fun StatsScreen(state: StatsUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ArchiveHeader(start = { BackButton(onBack) })
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> Text(
                    "Menghitung catatan baca...",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.Center),
                )
                state.summary != null -> StatsContent(state, state.summary)
                else -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(state.error.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                    ArchiveButton("Coba lagi", onRetry)
                }
            }
        }
    }
}

/** Numbers are set as type, not tiles: the three that answer "am I reading?" lead, the rest follow as a list. */
@Composable
private fun StatsContent(state: StatsUiState, summary: StatsSummary) {
    Row(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 64.dp, end = 64.dp, top = 40.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp),
    ) {
        Column(Modifier.weight(1f).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Column {
                MetaLine("Catatan baca")
                Text("Statistik", style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Figure("Hari ini", "${summary.todayMinutes}", "menit, ${summary.todayPages} halaman")
                Figure("Minggu ini", "${summary.weekMinutes}", "menit, ${summary.weekActiveDays} hari aktif")
                Figure("Runtutan", "${summary.currentStreakDays}", "hari, terpanjang ${summary.longestStreakDays}")
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Line("Sepanjang waktu", "${summary.totalMinutes} menit, ${summary.totalPages} halaman, ${summary.totalSessions} sesi")
                Line("Buku selesai", "${summary.booksFinishedThisYear} tahun ini, ${summary.booksFinishedAllTime} total")
                Line("Halaman minggu ini", "${summary.weekPages}")
            }
            Text(
                "Menit dihitung dari waktu aktif di reader dan dibulatkan ke bawah oleh server.",
                style = MaterialTheme.typography.bodySmall,
                color = Archive.Faint,
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetaLine("7 hari terakhir")
                state.daily.forEach { DayLine(it) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MetaLine("Buku paling lama dibaca")
                if (state.books.isEmpty()) {
                    Text("Belum ada sesi baca. Buka buku dari rak, dan waktunya tercatat di sini.", style = MaterialTheme.typography.bodyLarge)
                }
                state.books.forEachIndexed { index, book ->
                    Line("${index + 1}. ${book.title}", "${book.minutes} menit, ${book.pages} halaman", titleStyle = true)
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, unit: String) {
    Column {
        MetaLine(label)
        // Lining figures, so a zero never reads as the letter O.
        Text(value, style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "lnum"), modifier = Modifier.padding(top = 4.dp))
        Text(unit, style = MaterialTheme.typography.bodySmall, color = Archive.Muted)
    }
}

@Composable
private fun DayLine(day: DailyStat) {
    Line(
        "${DAY_NAMES[day.date.dayOfWeek.ordinal]}, ${day.date.day}/${day.date.month.ordinal + 1}",
        if (day.minutes == 0 && day.pages == 0) "tidak membaca" else "${day.minutes} menit, ${day.pages} halaman",
        muted = day.minutes == 0 && day.pages == 0,
    )
}

@Composable
private fun Line(label: String, value: String, titleStyle: Boolean = false, muted: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                label,
                style = if (titleStyle) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (muted) Archive.Faint else Archive.Muted,
                modifier = Modifier.padding(start = 16.dp).width(220.dp),
            )
        }
        HorizontalDivider(color = Archive.Line)
    }
}
