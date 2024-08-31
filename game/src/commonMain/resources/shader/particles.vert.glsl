uniform mat4 u_projTrans;
uniform float u_time;
uniform int u_interpolation;
attribute vec4 vertexPosition;
attribute vec4 startPosition;
attribute vec4 endPosition;
attribute vec4 startColor;
attribute vec4 endColor;
attribute vec2 activeBetween;
varying lowp vec4 v_color;

/**   0) linear: f(t) = t
    * 1) acceleration: f(t) = t^2
    * 2) decceleration = f(t) = 1 - (1 - t)^2
    * 3) smoothstep: f(t) = t^2*(3 - 2t)*/
float interpolate(float value, int interpolation) {
    if (interpolation == 0) {
        return value;
    } else if (interpolation == 1) {
        return value * value;
    } else if (interpolation == 2) {
        return 1.0 - pow(1.0 - value, 2.0);
    } else if (interpolation == 3) {
        return value * value * (3.0 - 2.0 * value);
    }
}

void main()
{
    float activePeriod = activeBetween.y - activeBetween.x;
    float timeRate = (u_time - activeBetween.x) / activePeriod;
    float clampedTimeRate = clamp(timeRate, 0.0, 1.0);
    float rate = interpolate(clampedTimeRate, u_interpolation);

    v_color = mix(startColor, endColor, rate);
    v_color.a = v_color.a * (255.0/254.0);

    vec4 pos = vertexPosition + mix(startPosition, endPosition, rate);

    gl_Position = u_projTrans * pos;
}
