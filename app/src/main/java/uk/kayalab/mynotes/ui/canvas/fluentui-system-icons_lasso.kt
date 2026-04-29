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

val FluentuiSystemIconsLasso: ImageVector
    get() {
        if (_FluentuiSystemIconsLasso != null) return _FluentuiSystemIconsLasso!!
        
        _FluentuiSystemIconsLasso = ImageVector.Builder(
            name = "lasso",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black)
            ) {
                moveTo(9.70298f, 2.2653f)
                curveTo(10.4415f, 2.09164f, 11.2107f, 2f, 12f, 2f)
                curveTo(12.7893f, 2f, 13.5585f, 2.09164f, 14.297f, 2.2653f)
                curveTo(14.8346f, 2.39173f, 15.168f, 2.93004f, 15.0416f, 3.46766f)
                curveTo(14.9151f, 4.00528f, 14.3768f, 4.33862f, 13.8392f, 4.2122f)
                curveTo(13.2497f, 4.07357f, 12.6341f, 4f, 12f, 4f)
                curveTo(11.3659f, 4f, 10.7503f, 4.07357f, 10.1608f, 4.2122f)
                curveTo(9.62317f, 4.33862f, 9.08486f, 4.00528f, 8.95844f, 3.46766f)
                curveTo(8.83201f, 2.93004f, 9.16536f, 2.39173f, 9.70298f, 2.2653f)
                close()
                moveTo(15.8825f, 3.81676f)
                curveTo(16.1734f, 3.34725f, 16.7897f, 3.2024f, 17.2592f, 3.49322f)
                curveTo(18.577f, 4.30945f, 19.6905f, 5.42304f, 20.5068f, 6.74076f)
                curveTo(20.7976f, 7.21027f, 20.6528f, 7.82664f, 20.1832f, 8.11747f)
                curveTo(19.7137f, 8.4083f, 19.0974f, 8.26344f, 18.8065f, 7.79393f)
                curveTo(18.1531f, 6.73901f, 17.261f, 5.84691f, 16.2061f, 5.19347f)
                curveTo(15.7366f, 4.90264f, 15.5917f, 4.28627f, 15.8825f, 3.81676f)
                close()
                moveTo(8.11747f, 3.81676f)
                curveTo(8.4083f, 4.28627f, 8.26344f, 4.90264f, 7.79393f, 5.19347f)
                curveTo(6.73901f, 5.84691f, 5.84691f, 6.73901f, 5.19347f, 7.79393f)
                curveTo(4.90264f, 8.26344f, 4.28627f, 8.4083f, 3.81676f, 8.11747f)
                curveTo(3.34725f, 7.82665f, 3.2024f, 7.21027f, 3.49322f, 6.74076f)
                curveTo(4.30945f, 5.42304f, 5.42304f, 4.30945f, 6.74076f, 3.49322f)
                curveTo(7.21027f, 3.2024f, 7.82664f, 3.34725f, 8.11747f, 3.81676f)
                close()
                moveTo(3.46766f, 8.95843f)
                curveTo(4.00528f, 9.08486f, 4.33862f, 9.62317f, 4.2122f, 10.1608f)
                curveTo(4.07357f, 10.7503f, 4f, 11.3659f, 4f, 12f)
                curveTo(4f, 12.6341f, 4.07357f, 13.2497f, 4.2122f, 13.8392f)
                curveTo(4.33862f, 14.3768f, 4.00528f, 14.9151f, 3.46766f, 15.0416f)
                curveTo(2.93004f, 15.168f, 2.39173f, 14.8346f, 2.2653f, 14.297f)
                curveTo(2.09164f, 13.5585f, 2f, 12.7893f, 2f, 12f)
                curveTo(2f, 11.2107f, 2.09164f, 10.4415f, 2.2653f, 9.70298f)
                curveTo(2.39173f, 9.16536f, 2.93004f, 8.83201f, 3.46766f, 8.95843f)
                close()
                moveTo(20.5323f, 8.95844f)
                curveTo(21.07f, 8.83201f, 21.6083f, 9.16536f, 21.7347f, 9.70298f)
                curveTo(21.9084f, 10.4415f, 22f, 11.2107f, 22f, 12f)
                curveTo(22f, 12.7893f, 21.9084f, 13.5585f, 21.7347f, 14.297f)
                curveTo(21.6083f, 14.8346f, 21.07f, 15.168f, 20.5323f, 15.0416f)
                curveTo(19.9947f, 14.9151f, 19.6614f, 14.3768f, 19.7878f, 13.8392f)
                curveTo(19.9264f, 13.2497f, 20f, 12.6341f, 20f, 12f)
                curveTo(20f, 11.3659f, 19.9264f, 10.7503f, 19.7878f, 10.1608f)
                curveTo(19.6614f, 9.62317f, 19.9947f, 9.08486f, 20.5323f, 8.95844f)
                close()
                moveTo(3.81676f, 15.8825f)
                curveTo(4.28627f, 15.5917f, 4.90264f, 15.7366f, 5.19347f, 16.2061f)
                curveTo(5.84691f, 17.261f, 6.73901f, 18.1531f, 7.79393f, 18.8065f)
                curveTo(8.26344f, 19.0974f, 8.4083f, 19.7137f, 8.11747f, 20.1832f)
                curveTo(7.82665f, 20.6528f, 7.21027f, 20.7976f, 6.74076f, 20.5068f)
                curveTo(5.42304f, 19.6906f, 4.30945f, 18.577f, 3.49322f, 17.2592f)
                curveTo(3.2024f, 16.7897f, 3.34725f, 16.1734f, 3.81676f, 15.8825f)
                close()
                moveTo(20.6217f, 17.4903f)
                curveTo(20.8925f, 17.0089f, 20.7218f, 16.3992f, 20.2404f, 16.1284f)
                curveTo(19.7598f, 15.858f, 19.1514f, 16.0273f, 18.88f, 16.5072f)
                lineTo(18.8793f, 16.5084f)
                lineTo(18.8788f, 16.5093f)
                lineTo(18.8663f, 16.5304f)
                curveTo(18.8538f, 16.5513f, 18.8328f, 16.5855f, 18.8035f, 16.6311f)
                curveTo(18.7448f, 16.7224f, 18.653f, 16.8587f, 18.5283f, 17.025f)
                curveTo(18.3476f, 17.2659f, 18.1007f, 17.566f, 17.7886f, 17.882f)
                curveTo(16.5966f, 16.8694f, 14.9938f, 16f, 12.9999f, 16f)
                curveTo(10.7333f, 16f, 9.00005f, 17.2f, 9f, 19f)
                curveTo(8.99995f, 20.8f, 10.7332f, 22f, 12.9999f, 22f)
                curveTo(14.9101f, 22f, 16.4586f, 21.3659f, 17.6391f, 20.5854f)
                curveTo(17.9855f, 21.0105f, 18.2612f, 21.4177f, 18.4626f, 21.744f)
                curveTo(18.595f, 21.9585f, 18.6937f, 22.1354f, 18.758f, 22.2559f)
                curveTo(18.7901f, 22.316f, 18.8135f, 22.3619f, 18.8281f, 22.3911f)
                lineTo(18.8433f, 22.422f)
                lineTo(18.8455f, 22.4265f)
                lineTo(18.846f, 22.4276f)
                curveTo(19.0818f, 22.9261f, 19.6768f, 23.1397f, 20.1759f, 22.9048f)
                curveTo(20.6756f, 22.6696f, 20.89f, 22.0738f, 20.6548f, 21.5741f)
                curveTo(20.5376f, 21.3428f, 20.6541f, 21.5726f, 20.6541f, 21.5726f)
                lineTo(20.6531f, 21.5706f)
                lineTo(20.6506f, 21.5653f)
                lineTo(20.6429f, 21.5494f)
                curveTo(20.639f, 21.5413f, 20.6342f, 21.5314f, 20.6284f, 21.5197f)
                curveTo(20.6249f, 21.5127f, 20.6211f, 21.505f, 20.6169f, 21.4966f)
                curveTo(20.5949f, 21.4526f, 20.5634f, 21.391f, 20.5226f, 21.3144f)
                curveTo(20.441f, 21.1615f, 20.3215f, 20.9478f, 20.1645f, 20.6935f)
                curveTo(19.9269f, 20.3086f, 19.5998f, 19.8247f, 19.184f, 19.3152f)
                curveTo(19.5835f, 18.9137f, 19.8979f, 18.5322f, 20.1283f, 18.225f)
                curveTo(20.2848f, 18.0163f, 20.4039f, 17.8401f, 20.4859f, 17.7127f)
                curveTo(20.5269f, 17.6489f, 20.5587f, 17.5971f, 20.5813f, 17.5594f)
                curveTo(20.5926f, 17.5406f, 20.6016f, 17.5252f, 20.6084f, 17.5136f)
                lineTo(20.6168f, 17.4991f)
                lineTo(20.6197f, 17.4939f)
                lineTo(20.6208f, 17.4919f)
                lineTo(20.6217f, 17.4903f)
                close()
                moveTo(12.9999f, 18f)
                curveTo(14.2257f, 18f, 15.2857f, 18.4757f, 16.1689f, 19.1445f)
                curveTo(15.3081f, 19.6407f, 14.2531f, 20f, 12.9999f, 20f)
                curveTo(11.2666f, 20f, 11f, 19.2f, 11f, 19f)
                curveTo(11f, 18.8f, 11.2667f, 18f, 12.9999f, 18f)
                close()
            }
        }.build()
        
        return _FluentuiSystemIconsLasso!!
    }

private var _FluentuiSystemIconsLasso: ImageVector? = null