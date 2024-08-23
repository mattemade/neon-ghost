package io.itch.mattemade.utils.math

import com.littlekt.math.MutableVec2f
import com.littlekt.math.Vec2f
import com.littlekt.math.geom.cos
import com.littlekt.math.geom.sin

private val mathTempVec2f = MutableVec2f()
fun belongsToEllipse(
    x: Float,
    y: Float,
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    extra: Float = 0f
): Boolean {
    mathTempVec2f.set(x, y).subtract(cx, cy)
    val distance = mathTempVec2f.length()
    val angle = mathTempVec2f.angleTo(Vec2f.X_AXIS)
    val referenceDistance = mathTempVec2f.set(
        rx * cos(angle),
        ry * sin(angle)
    ).length()
    return distance <= referenceDistance + extra
}