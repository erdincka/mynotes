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

val FluentuiSystemIconsEraser: ImageVector
    get() {
        if (_FluentuiSystemIconsEraser != null) return _FluentuiSystemIconsEraser!!
        
        _FluentuiSystemIconsEraser = ImageVector.Builder(
            name = "eraser",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(15.8697f, 2.66878f)
                lineTo(20.8382f, 7.6373f)
                curveTo(21.7169f, 8.51598f, 21.7169f, 9.9406f, 20.8382f, 10.8193f)
                lineTo(12.1564f, 19.4998f)
                lineTo(18.2543f, 19.5f)
                curveTo(18.634f, 19.5f, 18.9478f, 19.7821f, 18.9975f, 20.1482f)
                lineTo(19.0043f, 20.25f)
                curveTo(19.0043f, 20.6297f, 18.7222f, 20.9435f, 18.3561f, 20.9931f)
                lineTo(18.2543f, 21f)
                lineTo(9.84431f, 21.0012f)
                curveTo(9.2281f, 21.0348f, 8.6007f, 20.8163f, 8.12998f, 20.3456f)
                lineTo(3.16145f, 15.377f)
                curveTo(2.28277f, 14.4984f, 2.28277f, 13.0737f, 3.16145f, 12.1951f)
                lineTo(12.6877f, 2.66878f)
                curveTo(13.5664f, 1.7901f, 14.991f, 1.7901f, 15.8697f, 2.66878f)
                close()
                moveTo(11.6975f, 17.7583f)
                lineTo(5.74277f, 11.8035f)
                lineTo(4.23645f, 13.2701f)
                curveTo(3.94355f, 13.5629f, 3.94355f, 14.0378f, 4.23645f, 14.3307f)
                lineTo(9.18221f, 19.2763f)
                curveTo(9.47968f, 19.5646f, 9.9545f, 19.5571f, 10.2427f, 19.2596f)
                lineTo(11.6975f, 17.7583f)
                close()
            }
        }.build()
        
        return _FluentuiSystemIconsEraser!!
    }

private var _FluentuiSystemIconsEraser: ImageVector? = null