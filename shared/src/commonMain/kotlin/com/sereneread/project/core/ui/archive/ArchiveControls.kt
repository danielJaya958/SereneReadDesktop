package com.sereneread.project.core.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sereneread.project.core.ui.theme.Archive

private val ControlShape = RoundedCornerShape(3.dp)

/**
 * The boxed button from DESIGN.md: a 1dp ink outline that fills with ink on hover or keyboard focus.
 * [filled] marks the one primary action on a screen, which stays inked.
 */
@Composable
fun ArchiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val inked = enabled && (filled || hovered || focused)

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .border(1.dp, Archive.Ink, ControlShape)
            .background(if (inked) Archive.Ink else Color.Transparent, ControlShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (inked) Archive.Canvas else Archive.Ink,
        )
    }
}

/** A quiet text action for the header and secondary commands. It underlines on hover or focus. */
@Composable
fun ArchiveLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Archive.Ink,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()

    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            textDecoration = if (enabled && (hovered || focused)) TextDecoration.Underline else null,
        ),
        color = color,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(vertical = 6.dp),
    )
}

/** A filter pill. Only the selected pill takes the blue, so the accent marks the current choice. */
@Composable
fun ArchivePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(4.dp)

    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Archive.Paper else Archive.Ink,
        modifier = Modifier
            .background(if (selected) Archive.Blue else Color.Transparent, shape)
            .border(1.dp, if (selected) Archive.Blue else if (focused) Archive.Ink else Archive.Line, shape)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Top-left back control from DESIGN.md. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ArchiveButton("← Kembali", onClick, modifier)
}

/** A text field drawn as a thin ink frame on paper, without leading icons. */
@Composable
fun ArchiveField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Archive.Ink),
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Archive.Ink,
            unfocusedBorderColor = Archive.Line,
            focusedLabelColor = Archive.Ink,
            unfocusedLabelColor = Archive.Muted,
            cursorColor = Archive.Ink,
            focusedContainerColor = Archive.Paper,
            unfocusedContainerColor = Color.Transparent,
        ),
    )
}

/**
 * The persistent header: brand centered, an optional control on the left, text actions on the right.
 */
@Composable
fun ArchiveHeader(
    modifier: Modifier = Modifier,
    start: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 28.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { start() }
        Text(
            "SERENEREAD",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, letterSpacing = 9.sp),
            color = Archive.Brand,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Accent metadata line, such as "PDF / 654 HALAMAN / 2011". */
@Composable
fun MetaLine(text: String, modifier: Modifier = Modifier, color: Color = Archive.Accent) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** A short status or error line under the content, in the place DESIGN.md gives the footer. */
@Composable
fun FooterNote(text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) Archive.Oxblood else Archive.Faint,
        modifier = modifier,
    )
}
