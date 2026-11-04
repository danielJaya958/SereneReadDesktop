package com.sereneread.project.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import sereneread.shared.generated.resources.Res
import sereneread.shared.generated.resources.playfair_display
import sereneread.shared.generated.resources.playfair_display_italic
import sereneread.shared.generated.resources.plus_jakarta_sans

/** Colors from DESIGN.md: a warm archival paper canvas with deep print inks. */
object Archive {
    val Canvas = Color(0xFFF5F0E6)
    val Paper = Color(0xFFFAF7F0)
    val Line = Color(0xFFD6CFC2)
    val Blue = Color(0xFF2352C6)
    val Ink = Color(0xFF181818)
    val InkBody = Color(0xFF2B2B2B)
    val Muted = Color(0xFF6E6A63)
    val Faint = Color(0xFF8C857B)
    val Accent = Color(0xFFC87D4B)
    val Brand = Color(0xFF4A69BD)
    val Oxblood = Color(0xFF7A1F1D)

    /** Only the cover that is lifted off the shelf casts a shadow, in this warm tint. */
    val Shadow = Color(0x332B2318)

    /** Book spine inks. The last one is the light ochre spine, which needs dark text. */
    val SpineInks = listOf(
        Color(0xFF16468B),
        Color(0xFF1D4D3E),
        Color(0xFF7A1F1D),
        Color(0xFF222930),
        Color(0xFFDFD5C0),
    )
    val OchreSpine = Color(0xFFDFD5C0)
    val OchreText = Color(0xFF2B2B2B)
}

/** Playfair Display reads like a printed catalog; Plus Jakarta Sans is an Indonesian face for controls. */
@Composable
private fun serifFamily() = FontFamily(
    Font(Res.font.playfair_display, FontWeight.Normal),
    Font(Res.font.playfair_display, FontWeight.Medium),
    Font(Res.font.playfair_display, FontWeight.SemiBold),
    Font(Res.font.playfair_display_italic, FontWeight.Normal, FontStyle.Italic),
)

@Composable
private fun sansFamily() = FontFamily(
    Font(Res.font.plus_jakarta_sans, FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans, FontWeight.Medium),
    Font(Res.font.plus_jakarta_sans, FontWeight.SemiBold),
)

private val Colors = lightColorScheme(
    primary = Archive.Blue,
    onPrimary = Archive.Paper,
    secondary = Archive.Accent,
    onSecondary = Archive.Paper,
    background = Archive.Canvas,
    onBackground = Archive.Ink,
    surface = Archive.Canvas,
    onSurface = Archive.Ink,
    surfaceVariant = Archive.Paper,
    onSurfaceVariant = Archive.Muted,
    surfaceContainerLowest = Archive.Paper,
    surfaceContainerLow = Archive.Paper,
    surfaceContainer = Archive.Paper,
    surfaceContainerHigh = Archive.Paper,
    surfaceContainerHighest = Archive.Paper,
    outline = Archive.Line,
    outlineVariant = Archive.Line,
    error = Archive.Oxblood,
    onError = Archive.Paper,
    inverseSurface = Archive.Ink,
    inverseOnSurface = Archive.Canvas,
)

/**
 * Serif styles carry titles and reading text; sans styles carry controls and metadata.
 * Uppercase labels are written in uppercase at the call site, because Compose has no text transform.
 */
@Composable
private fun archiveTypography(): Typography {
    val serif = serifFamily()
    val sans = sansFamily()
    return Typography(
        displayLarge = TextStyle(fontFamily = serif, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1).sp),
        displayMedium = TextStyle(fontFamily = serif, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = serif, fontSize = 28.sp, lineHeight = 34.sp),
        headlineSmall = TextStyle(fontFamily = serif, fontSize = 22.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = serif, fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium = TextStyle(fontFamily = serif, fontSize = 17.sp, lineHeight = 22.sp),
        titleSmall = TextStyle(fontFamily = serif, fontSize = 15.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = serif, fontSize = 15.sp, lineHeight = 24.sp, color = Archive.InkBody),
        bodyMedium = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = sans, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = sans, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
        labelMedium = TextStyle(fontFamily = sans, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
        labelSmall = TextStyle(fontFamily = sans, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
    )
}

/** Two radii only: 3dp for controls and 4dp for pills, dialogs, and menus. */
private val ArchiveShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** A single light theme: DESIGN.md defines the whole app on a warm paper canvas. */
@Composable
fun SereneReadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = archiveTypography(),
        shapes = ArchiveShapes,
        content = content,
    )
}
