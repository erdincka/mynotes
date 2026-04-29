package uk.kayalab.mynotes.ui.canvas/*
MIT License

Copyright (c) 2020 Microsoft Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FluentuiSystemIconsHighlight: ImageVector
    get() {
        if (_FluentuiSystemIconsHighlight != null) return _FluentuiSystemIconsHighlight!!
        
        _FluentuiSystemIconsHighlight = ImageVector.Builder(
            name = "highlight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(5.25f, 2f)
                curveTo(4.00736f, 2f, 3f, 3.00736f, 3f, 4.25f)
                verticalLineTo(7.25f)
                curveTo(3f, 8.49264f, 4.00736f, 9.5f, 5.25f, 9.5f)
                horizontalLineTo(18.75f)
                curveTo(19.9926f, 9.5f, 21f, 8.49264f, 21f, 7.25f)
                verticalLineTo(4.25f)
                curveTo(21f, 3.00736f, 19.9926f, 2f, 18.75f, 2f)
                horizontalLineTo(5.25f)
                close()
                moveTo(5f, 11.75f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                verticalLineTo(11.75f)
                curveTo(19f, 12.9926f, 17.9926f, 14f, 16.75f, 14f)
                horizontalLineTo(7.25f)
                curveTo(6.00736f, 14f, 5f, 12.9926f, 5f, 11.75f)
                close()
                moveTo(7.50294f, 15.5f)
                horizontalLineTo(16.5013f)
                lineTo(16.5017f, 16.7881f)
                curveTo(16.5017f, 17.6031f, 16.0616f, 18.3494f, 15.36f, 18.7463f)
                lineTo(15.2057f, 18.8259f)
                lineTo(8.57101f, 21.9321f)
                curveTo(8.10478f, 22.1504f, 7.57405f, 21.8451f, 7.50953f, 21.3536f)
                lineTo(7.503f, 21.2529f)
                lineTo(7.50294f, 15.5f)
                close()
            }
        }.build()
        
        return _FluentuiSystemIconsHighlight!!
    }

private var _FluentuiSystemIconsHighlight: ImageVector? = null