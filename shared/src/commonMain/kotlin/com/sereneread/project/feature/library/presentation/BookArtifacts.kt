package com.sereneread.project.feature.library.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneread.project.core.ui.theme.Archive
import kotlin.random.Random

/** One book on the shelf, whether it lives on the server or only on this computer. */
data class ShelfBook(
    val key: String,
    val title: String,
    val author: String?,
    val year: Int?,
    val format: String,
    val progressText: String?,
    val thumbnailUrl: String?,
    val isFavorite: Boolean,
    val remoteId: Long? = null,
    val isDownloaded: Boolean = false,
) {
    val ink: Color get() = Archive.SpineInks[(key.hashCode() and Int.MAX_VALUE) % Archive.SpineInks.size]
    val inkText: Color get() = if (ink == Archive.OchreSpine) Archive.OchreText else Archive.Paper
}

/**
 * Printed-object wear: darker card edges and a fixed paper grain, seeded per book so it never shimmers.
 * It replaces the clean vector look that DESIGN.md rules out.
 */
fun Modifier.paperWear(seed: String): Modifier = drawWithCache {
    val random = Random(seed.hashCode())
    val grain = List(size.width.toInt() * size.height.toInt() / 900) {
        Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
    }
    // Two batched draws instead of one call per speck: half lightens the ink, half darkens it.
    val light = grain.filterIndexed { index, _ -> index % 2 == 0 }
    val dark = grain.filterIndexed { index, _ -> index % 2 == 1 }
    val speck = 1.6f
    val edge = 6.dp.toPx().coerceAtMost(size.width / 4)
    val shade = Color.Black.copy(alpha = 0.18f)
    onDrawWithContent {
        drawContent()
        drawPoints(light, PointMode.Points, Color.White.copy(alpha = 0.06f), strokeWidth = speck, cap = StrokeCap.Round)
        drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = 0.06f), strokeWidth = speck, cap = StrokeCap.Round)
        drawRect(Brush.horizontalGradient(listOf(shade, Color.Transparent), endX = edge), size = Size(edge, size.height))
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, shade), startX = size.width - edge, endX = size.width),
            topLeft = Offset(size.width - edge, 0f),
            size = Size(edge, size.height),
        )
        drawRect(Brush.verticalGradient(listOf(shade, Color.Transparent), endY = edge), size = Size(size.width, edge))
    }
}

/** Lays the child out rotated 90°, so a long title reads down the spine. */
private fun Modifier.vertical(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth),
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = -(placeable.width - placeable.height) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = 90f }
    }
}

/** Loads a cover once per URL; null while loading, when there is no cover, or when it fails. */
@Composable
private fun rememberCover(url: String?, loadCover: suspend (String) -> ImageBitmap?): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, url) { value = url?.let { loadCover(it) } }
    return image
}

/**
 * A spine shows a centre strip of the book's own cover when the server has one, so the shelf already
 * looks like the real books. Books without a cover keep the printed ink spine with a vertical title.
 */
@Composable
fun BookSpine(
    book: ShelfBook,
    number: Int,
    loadCover: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val cover = rememberCover(book.thumbnailUrl, loadCover)
    val textColor = if (cover != null) Archive.Paper else book.inkText

    Box(modifier.clip(RoundedCornerShape(3.dp)).background(book.ink)) {
        if (cover != null) {
            Image(cover, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // Darkens only the ends, so the number and format stay readable on any artwork.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.18f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
            )
        }
        Column(
            Modifier.fillMaxSize().paperWear(book.key).padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(number.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = textColor)
            Box(Modifier.weight(1f).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                if (cover == null) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.vertical(),
                    )
                }
            }
            FormatEmblem(book.format, textColor)
        }
    }
}

@Composable
private fun FormatEmblem(format: String, color: Color) {
    Text(
        format,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.5.sp),
        color = color,
        modifier = Modifier.border(1.dp, color, RoundedCornerShape(2.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

/** The front cover: the server's cover image when there is one, otherwise a printed title card. */
@Composable
fun BookFront(
    book: ShelfBook,
    loadCover: suspend (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    titleSize: Int = 26,
) {
    val image = rememberCover(book.thumbnailUrl, loadCover)

    Box(modifier.clip(RoundedCornerShape(3.dp)).background(book.ink).paperWear(book.key)) {
        val current = image
        if (current != null) {
            Image(current, contentDescription = "Sampul ${book.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(
                Modifier.fillMaxSize().padding(22.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    book.author.orEmpty().uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = book.inkText.copy(alpha = 0.8f),
                    maxLines = 2,
                )
                Text(
                    book.title,
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = titleSize.sp, lineHeight = (titleSize + 4).sp),
                    color = book.inkText,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
                FormatEmblem(book.format, book.inkText)
            }
        }
    }
}

/** The paper back seen halfway through a flip, with the round open cue from DESIGN.md. */
@Composable
fun BookBack(modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(3.dp)).background(Archive.Paper).paperWear("back"), contentAlignment = Alignment.Center) {
        OpenCue()
    }
}

@Composable
fun OpenCue(modifier: Modifier = Modifier) {
    Box(modifier.size(56.dp).background(Archive.Blue, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            "BUKA",
            style = MaterialTheme.typography.labelLarge,
            color = Archive.Paper,
            textAlign = TextAlign.Center,
        )
    }
}
