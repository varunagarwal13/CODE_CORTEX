package com.vocis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Official Brand Colors from Logo
val VocisLogoGreen = Color(0xFF3B6547)
val VocisLogoSageBg = Color(0xFFE7EFE9)

/**
 * Renders the official VOCIS geometric diamond emblem:
 * - Outer rotated square diamond frame with precise stroke
 * - Inner solid rotated square diamond core
 */
@Composable
fun VocisLogoEmblem(
    modifier: Modifier = Modifier,
    color: Color = VocisLogoGreen,
    strokeWidthRatio: Float = 0.075f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        val outerRadius = w * 0.40f
        val strokeWidth = w * strokeWidthRatio

        // Outer Diamond Path
        val outerPath = Path().apply {
            moveTo(cx, cy - outerRadius)
            lineTo(cx + outerRadius, cy)
            lineTo(cx, cy + outerRadius)
            lineTo(cx - outerRadius, cy)
            close()
        }

        drawPath(
            path = outerPath,
            color = color,
            style = Stroke(width = strokeWidth)
        )

        // Inner Solid Diamond Core
        val innerRadius = w * 0.17f
        val innerPath = Path().apply {
            moveTo(cx, cy - innerRadius)
            lineTo(cx + innerRadius, cy)
            lineTo(cx, cy + innerRadius)
            lineTo(cx - innerRadius, cy)
            close()
        }

        drawPath(
            path = innerPath,
            color = color
        )
    }
}

/**
 * VOCIS Logo Icon Badge with optional rounded pale sage background card.
 */
@Composable
fun VocisLogoBadge(
    size: Dp = 48.dp,
    withBackground: Boolean = true,
    backgroundColor: Color = VocisLogoSageBg,
    emblemColor: Color = VocisLogoGreen,
    modifier: Modifier = Modifier
) {
    if (withBackground) {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            VocisLogoEmblem(
                modifier = Modifier.size(size * 0.72f),
                color = emblemColor
            )
        }
    } else {
        VocisLogoEmblem(
            modifier = modifier.size(size),
            color = emblemColor
        )
    }
}
