uniform sampler2D u_texture;
uniform sampler2D u_overlayTexture;
uniform vec4 u_textureSizes;
uniform vec4 u_sampleProperties;
varying lowp vec4 v_color;
varying vec2 v_texCoords;

const float gamePadding = 0.1;
const float overlayPadding = 0.05;

const float negativeGamePadding = 1.0 - gamePadding;
const float negativeOverlayPadding = 1.0 - overlayPadding;
const vec4 gameBounds = vec4(gamePadding, gamePadding, negativeGamePadding, negativeGamePadding);
const vec4 overlayBounds = vec4(overlayPadding, overlayPadding, negativeOverlayPadding, negativeOverlayPadding);
const vec4 overlayFullBounds = vec4(0.0, 0.0, 1.0, 1.0);
const vec4 nothing = vec4(0.0, 0.0, 0.0, 0.0);
const float reflectionPositionFactor = 1.0;
const float gameReflectionColorFactor = 0.4;
const float ghostReflectionColorFactor = 0.2;
const float smallCornerR = 0.05;
const vec2 virtualResolution = vec2(320, 240);
const vec2 blurDirection = vec2(-3.0, 0.0);

vec3 draw(sampler2D image, vec2 uv) {
    return texture2D(image,vec2(uv.x, uv.y)).rgb;
}
float rand(vec2 co){
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}
vec4 blur(vec2 uv, sampler2D image, float blurAmount){
    float alpha = texture2D(image, uv).a;
    vec3 blurredImage = vec3(0.);
    float d = smoothstep(0.8, 0.0, (gl_FragCoord.y / virtualResolution.y) / virtualResolution.y);
    #define repeats 40.
  for (float i = 0.; i < repeats; i++) {
        vec2 q = vec2(cos(degrees((i / repeats) * 360.)), sin(degrees((i / repeats) * 360.))) * (rand(vec2(i, uv.x + uv.y)) + blurAmount);
        vec2 uv2 = uv + (q * blurAmount * d);
        blurredImage += draw(image, uv2) / 2.;
        q = vec2(cos(degrees((i / repeats) * 360.)), sin(degrees((i / repeats) * 360.))) * (rand(vec2(i + 2., uv.x + uv.y + 24.)) + blurAmount);
        uv2 = uv + (q * blurAmount * d);
        blurredImage += draw(image, uv2) / 2.;
    }
    return vec4(blurredImage / repeats, alpha);
}


vec4 blur13(sampler2D image, vec2 uv, vec2 resolution, vec2 direction) {
    vec4 color = vec4(0.0);
    vec2 off1 = vec2(1.411764705882353) * direction;
    vec2 off2 = vec2(3.2941176470588234) * direction;
    vec2 off3 = vec2(5.176470588235294) * direction;
    color += texture2D(image, uv) * 0.1964825501511404;
    color += texture2D(image, uv + (off1 / resolution)) * 0.2969069646728344;
    color += texture2D(image, uv - (off1 / resolution)) * 0.2969069646728344;
    color += texture2D(image, uv + (off2 / resolution)) * 0.09447039785044732;
    color += texture2D(image, uv - (off2 / resolution)) * 0.09447039785044732;
    color += texture2D(image, uv + (off3 / resolution)) * 0.010381362401148057;
    color += texture2D(image, uv - (off3 / resolution)) * 0.010381362401148057;
    return color;
}

vec4 blur9(sampler2D image, vec2 uv, vec2 resolution, vec2 direction) {
    vec4 color = vec4(0.0);
    vec2 off1 = vec2(1.3846153846) * direction;
    vec2 off2 = vec2(3.2307692308) * direction;
    color += texture2D(image, uv) * 0.2270270270;
    color += texture2D(image, uv + (off1 / resolution)) * 0.3162162162;
    color += texture2D(image, uv - (off1 / resolution)) * 0.3162162162;
    color += texture2D(image, uv + (off2 / resolution)) * 0.0702702703;
    color += texture2D(image, uv - (off2 / resolution)) * 0.0702702703;
    return color;
}

vec2 revolved(vec2 point, vec2 center) {
    return center + (center - point);
}

float reflected(float value) {
    return value < 0.0 ? -value * reflectionPositionFactor : value > 1.0 ? 1.0 - ((value - 1.0) * reflectionPositionFactor) : value;
}

float relative(float value, float left, float right) {
    return (value - left) / (right - left);
}

float bounded(float value, float left, float right) {
    return clamp(0.0, 1.0, relative(value, left, right));
}

vec2 inBounds(vec2 uv, vec4 bounds) {
    return vec2(
        bounded(uv.x, bounds.x, bounds.z),
        bounded(uv.y, bounds.y, bounds.w)
    );
}

vec4 colorFromBounds(sampler2D tex, vec2 uv, vec4 bounds, float reflectionColorFactor) {
/**    bool inTheCorner = uv.x < bounds.x + smallCornerR && uv.y < bounds.y + smallCornerR;
    bool validCorner = uv.x > bounds.x && uv.y > bounds.y;
    if (inTheCorner && !validCorner) {
        uv = revolved(uv, bounds.xy + smallCornerR);
    }*/

    vec2 withReflections = vec2(
        relative(uv.x, bounds.x, bounds.z),
        relative(uv.y, bounds.y, bounds.w)
    );
    float factor = 1.0;
    if (withReflections.x < 0.0 || withReflections.y < 0.0 || withReflections.x > 1.0 || withReflections.y > 1.0) {
        factor = reflectionColorFactor;
        /**if (uv.x < extraBounds.x || uv.x > extraBounds.z || uv.y < extraBounds.y || uv.y > extraBounds.w) {
            factor /= 1.1;
        }*/
        withReflections.x = reflected(withReflections.x);
        withReflections.y = reflected(withReflections.y);
        return blur(withReflections, tex, 0.08) * factor;
        //return blur13(tex, withReflections, virtualResolution, blurDirection) * factor;
    }
    return texture2D(tex, withReflections);
}

void main() {
    vec4 gameColor = v_color * colorFromBounds(u_texture, v_texCoords, gameBounds, gameReflectionColorFactor);
    vec4 overlayColor = v_color * colorFromBounds(u_overlayTexture, v_texCoords, overlayBounds, ghostReflectionColorFactor);
    //vec4 color = (gameColor + overlayColor) / 2;
    gl_FragColor = gameColor + overlayColor;
}
