package io.legado.app.ui.widget.components.text

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp

fun Modifier.hiddenAttributeUnderline(
    hidden: Boolean,
    color: Color
): Modifier = if (!hidden) {
    this
} else {
    drawBehind {
        val strokeWidth = 1.dp.toPx()
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(0f, size.height - strokeWidth),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height - strokeWidth),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        )
    }
}
