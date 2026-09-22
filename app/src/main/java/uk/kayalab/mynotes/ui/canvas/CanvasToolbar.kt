package uk.kayalab.mynotes.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import uk.kayalab.mynotes.data.PageTemplate
import uk.kayalab.mynotes.ui.theme.LocalIsDarkTheme

private val palette = listOf(
    Color.Black, Color(0xFF616161), Color(0xFF9E9E9E), Color.White,
    Color(0xFFD32F2F), Color(0xFFF57C00), Color(0xFFFBC02D), Color(0xFF388E3C),
    Color(0xFF0288D1), Color(0xFF1976D2), Color(0xFF7B1FA2), Color(0xFFC2185B)
)
private val fontChoices = listOf("Default", "Serif", "SansSerif", "Monospace")

@Composable
fun CanvasToolbar(
    currentTool: CanvasTool,
    onToolSelected: (CanvasTool) -> Unit,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    currentStrokeWidth: Float,
    onStrokeWidthChanged: (Float) -> Unit,
    currentFontSize: Float,
    onFontSizeChanged: (Float) -> Unit,
    currentFontFamily: String,
    onFontFamilyChanged: (String) -> Unit,
    template: PageTemplate,
    onTemplateSelected: (PageTemplate) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalIsDarkTheme.current
    var showStylePopover by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).fillMaxWidth()
        ) {
            ToolbarIconButton(FluentIcons.Back, "Back", onClick = onBack)
            ToolbarIconButton(FluentIcons.Undo, "Undo", onClick = onUndo)
            ToolbarIconButton(FluentIcons.Redo, "Redo", onClick = onRedo)

            VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 4.dp))

            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                CanvasTool.entries.forEach { tool ->
                    val icon = when (tool) {
                        CanvasTool.PEN -> FluentIcons.Pen
                        CanvasTool.BRUSH -> FluentIcons.Brush
                        CanvasTool.ERASER -> FluentIcons.Eraser
                        CanvasTool.HIGHLIGHTER -> FluentIcons.Highlighter
                        CanvasTool.LASSO -> FluentIcons.Lasso
                        CanvasTool.TEXT -> FluentIcons.Text
                    }
                    ToolbarIconButton(
                        icon = icon,
                        description = tool.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = tool == currentTool,
                        onClick = {
                            if (tool == currentTool && tool != CanvasTool.LASSO) showStylePopover = true else onToolSelected(tool)
                        }
                    )
                }
            }

            VerticalDivider(modifier = Modifier.height(28.dp).padding(horizontal = 4.dp))

            Box {
                StyleButton(
                    color = displayColor(currentColor, isDark),
                    width = currentStrokeWidth,
                    tool = currentTool,
                    onClick = { showStylePopover = true }
                )
                DropdownMenu(expanded = showStylePopover, onDismissRequest = { showStylePopover = false }) {
                    StylePopover(
                        tool = currentTool,
                        currentColor = currentColor,
                        onColorSelected = onColorSelected,
                        currentStrokeWidth = currentStrokeWidth,
                        onStrokeWidthChanged = onStrokeWidthChanged,
                        currentFontSize = currentFontSize,
                        onFontSizeChanged = onFontSizeChanged,
                        currentFontFamily = currentFontFamily,
                        onFontFamilyChanged = onFontFamilyChanged,
                        isDark = isDark
                    )
                }
            }

            Box {
                ToolbarIconButton(FluentIcons.More, "More", onClick = { showMenu = true })
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Send as PDF") }, onClick = { showMenu = false; onShare() })
                    DropdownMenuItem(text = { Text("Export PDF to folder") }, onClick = { showMenu = false; onExport() })
                    Text(
                        "Paper",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                    PageTemplate.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (option == template) "${option.label}  ✓" else option.label) },
                            onClick = { showMenu = false; onTemplateSelected(option) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(icon: ImageVector, description: String, onClick: () -> Unit, selected: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        colors = if (selected) IconButtonDefaults.filledIconButtonColors() else IconButtonDefaults.iconButtonColors()
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

/** Ink colours are stored as drawn on white; in the dark theme black shows as white and vice versa. */
private fun displayColor(color: Color, isDark: Boolean): Color = when {
    !isDark -> color
    color == Color.Black -> Color.White
    color == Color.White -> Color.Black
    else -> color
}

@Composable
private fun StyleButton(color: Color, width: Float, tool: CanvasTool, onClick: () -> Unit) {
    val dot = when (tool) {
        CanvasTool.ERASER -> 18.dp
        CanvasTool.TEXT -> 14.dp
        else -> (6 + width * 0.35f).dp.coerceAtMost(22.dp)
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(dot)
                .clip(CircleShape)
                .background(if (tool == CanvasTool.ERASER) Color.Transparent else color)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    }
}

@Composable
private fun StylePopover(
    tool: CanvasTool,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    currentStrokeWidth: Float,
    onStrokeWidthChanged: (Float) -> Unit,
    currentFontSize: Float,
    onFontSizeChanged: (Float) -> Unit,
    currentFontFamily: String,
    onFontFamilyChanged: (String) -> Unit,
    isDark: Boolean
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(300.dp)) {
        if (tool != CanvasTool.ERASER) {
            Text("Colour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            palette.chunked(6).forEach { rowColors ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    rowColors.forEach { color ->
                        val selected = color.toArgb() == currentColor.toArgb()
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(displayColor(color, isDark))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(color) }
                        )
                    }
                }
            }
        }
        if (tool == CanvasTool.TEXT) {
            Text("Font", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fontChoices.forEach { font ->
                    FilterChip(selected = font == currentFontFamily, onClick = { onFontFamilyChanged(font) }, label = { Text(font) })
                }
            }
            Text("Size ${currentFontSize.toInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Slider(value = currentFontSize, onValueChange = onFontSizeChanged, valueRange = 12f..120f)
        } else {
            val range = if (tool == CanvasTool.ERASER) 10f..240f else 1f..50f
            val label = if (tool == CanvasTool.ERASER) "Eraser size" else "Width"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$label ${currentStrokeWidth.toInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (tool != CanvasTool.ERASER) {
                    Box(
                        modifier = Modifier
                            .size((4 + currentStrokeWidth * 0.5f).dp.coerceAtMost(28.dp))
                            .clip(CircleShape)
                            .background(displayColor(currentColor, isDark))
                    )
                }
            }
            Slider(value = currentStrokeWidth.coerceIn(range), onValueChange = onStrokeWidthChanged, valueRange = range)
        }
    }
}
