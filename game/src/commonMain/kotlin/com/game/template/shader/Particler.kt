package com.game.template.shader

class Particler {

    /*
    * General design:
    * 0) uniform time
    * 1) has 4 buffers (same size) of vec4f, containing:
    *   * fromX, fromY, toX, toY
    *   * r, g, b
    *   * fromAlpha, toAlpha, interpolationType
    *   * beginTime, startTime, finishTime, endTime
    *
    * fromValue = vectorsN[gl_instanceID].x
    * toValue = vectorsN[gl_instanceID].y
    * value = fromValue + (toValue - fromValue) * interpolate(t, interpolationType)
    *
    * interpolationType selects the way to interpolate between from and to values
    * by f(t) where f(0) = from and f(1) = to and t is in [0, 1]
    * (t = clamp(0, 1, (time - startTime) / (finishTime - startTime)))
    *
    * types: https://codeplea.com/simple-interpolation
    * 1) linear: f(t) = t
    * 2) acceleration: f(t) = t^2
    * 3) decceleration = f(t) = 1 - (1 - t)^2
    * 4) smoothstep: f(t) = t^2*(3 - 2t)
    *
    *
    *
    *
    *
    */
}