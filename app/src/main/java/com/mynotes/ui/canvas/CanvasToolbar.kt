package com.mynotes.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasToolbar(
    currentTool: CanvasTool,
    onToolSelected: (CanvasTool) -> Unit,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    currentStrokeWidth: Float,
    onStrokeWidthChanged: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    currentFontSize: Float,
    onFontSizeChanged: (Float) -> Unit,
    currentFontFamily: String,
    onFontFamilyChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val baseColors = listOf(
        Color.Black, Color.DarkGray, Color.Gray, Color.LightGray, Color.White,
        Color.Red, Color.Magenta, Color.Yellow, Color.Green, Color.Cyan, Color.Blue
    )
    
    val displayColors = remember(isDark) {
        if (isDark) {
            baseColors.map { 
                if (it == Color.Black) Color.White
                else if (it == Color.White) Color.Black
                else it
            }
        } else {
            baseColors
        }
    }

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 4.dp))

                IconButton(onClick = onUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = onRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }

                VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 4.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CanvasTool.entries.forEach { tool ->
                        val icon = when (tool) {
                            CanvasTool.PEN -> Icons.Default.Edit
                            CanvasTool.BRUSH -> Icons.Default.Brush
                            CanvasTool.ERASER -> Icons.Default.AutoFixNormal
                            CanvasTool.HIGHLIGHTER -> Icons.Default.Highlight
                            CanvasTool.LASSO -> Icons.Default.Gesture
                            CanvasTool.TEXT -> Icons.Default.TextFields
                        }

                        IconButton(
                            onClick = { onToolSelected(tool) },
                            colors = if (tool == currentTool) {
                                IconButtonDefaults.filledIconButtonColors()
                            } else {
                                IconButtonDefaults.iconButtonColors()
                            }
                        ) {
                            Icon(icon, contentDescription = tool.name)
                        }
                    }
                }

                VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 8.dp))

                IconButton(onClick = onSave) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
            }

            // Second Row: Contextual Settings & Colors
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                // Color Selection
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp),
                    modifier = Modifier.weight(0.4f)
                ) {
                    items(displayColors) { color ->
                        val actualColor = if (isDark) {
                            if (color == Color.White) Color.Black
                            else if (color == Color.Black) Color.White
                            else color
                        } else {
                            color
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (actualColor.toArgb() == currentColor.toArgb()) 2.dp else 1.dp,
                                    color = if (actualColor.toArgb() == currentColor.toArgb()) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(actualColor) }
                        )
                    }
                }

                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 8.dp))

                // Contextual Settings
                Row(
                    modifier = Modifier.weight(0.6f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentTool == CanvasTool.TEXT) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(
                                onClick = { expanded = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(currentFontFamily, fontSize = 12.sp)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                listOf("Default", "Serif", "SansSerif", "Monospace").forEach { font ->
                                    DropdownMenuItem(
                                        text = { Text(font) },
                                        onClick = {
                                            onFontFamilyChanged(font)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Text("Size: ${currentFontSize.toInt()}", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = currentFontSize,
                            onValueChange = onFontSizeChanged,
                            valueRange = 8f..72f,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Icon(
                            Icons.Default.LineWeight, 
                            contentDescription = "Width", 
                            modifier = Modifier.size(20.dp), 
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Slider(
                            value = currentStrokeWidth,
                            onValueChange = onStrokeWidthChanged,
                            valueRange = 1f..50f,
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
