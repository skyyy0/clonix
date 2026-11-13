package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalLiquidGlass

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(glass.backdropTop, glass.backdropBottom)
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.ambientGlowPrimary, Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.12f),
                        radius = size.width * 0.85f
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.12f),
                    radius = size.width * 0.85f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.ambientGlowSecondary, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.55f),
                        radius = size.width * 0.78f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.55f),
                    radius = size.width * 0.78f
                )
            },
        content = content
    )
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevated: Boolean = false,
    accentRimColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = LocalLiquidGlass.current
    val fill = if (elevated) glass.glassFillElevated else glass.glassFill
    val rimTop = accentRimColor?.copy(alpha = 0.42f) ?: glass.glassSpecularTop
    val rimBottom = glass.glassSpecularBottom
    val borderBrush = Brush.linearGradient(
        colors = listOf(rimTop, rimBottom, rimTop.copy(alpha = 0.22f))
    )

    val baseModifier = modifier
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    fill.copy(alpha = (fill.alpha + 0.04f).coerceAtMost(1f)),
                    fill
                )
            ),
            shape = shape
        )
        .border(width = 0.75.dp, brush = borderBrush, shape = shape)
        .let { m ->
            if (onClick != null) {
                m.clickable(onClick = onClick).minimumInteractiveComponentSize()
            } else {
                m
            }
        }

    Box(modifier = baseModifier, content = content)
}

@Composable
fun StatusPillBadge(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.75.dp, color.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun GlassActionChip(
    label: String,
    icon: ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current
    val bgColor = if (selected) scheme.primary else glass.glassFill
    val borderColor = if (selected) scheme.primary else glass.glassSpecularTop.copy(alpha = 0.55f)
    val contentColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(0.75.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(6.dp))
            trailing()
        }
    }
}

@Composable
fun GlassDivider(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp
) {
    val glass = LocalLiquidGlass.current
    Box(
        modifier = modifier
            .padding(horizontal = horizontalPadding)
            .background(glass.glassSpecularBottom.copy(alpha = if (glass.isDark) 0.14f else 0.35f))
            .size(width = Dp.Unspecified, height = 0.75.dp)
    )
}
