uniform sampler2D u_texture;
uniform sampler2D u_overlayTexture;
uniform vec4 u_textureSizes;
uniform vec4 u_sampleProperties;
uniform vec2 u_resolution;
uniform float u_scale;
uniform float u_time;
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


//
// PUBLIC DOMAIN CRT STYLED SCAN-LINE SHADER
//
//   by Timothy Lottes
//
// This is more along the style of a really good CGA arcade monitor.
// With RGB inputs instead of NTSC.
// The shadow mask example has the mask rotated 90 degrees for less chromatic aberration.
//
// Left it unoptimized to show the theory behind the algorithm.
//
// It is an example what I personally would want as a display option for pixel art games.
// Please take and use, change, or whatever.
//

// Emulated input resolution.
#if 0
  // Fix resolution to set amount.
vec2 res=vec2(320.0/1.0,240.0/1.0);
#else
  // Optimize for resize.
#define res (u_resolution.xy/1.0)
#endif

// Hardness of scanline.
//  -8.0 = soft
// -16.0 = medium
float hardScan=-24.0;

// Hardness of pixels in scanline.
// -2.0 = soft
// -4.0 = hard
float hardPix=-2.0;

// Display warp.
// 0.0 = none
// 1.0/8.0 = extreme
vec2 warp=vec2(0.0,0.0);

// Amount of shadow mask.
float maskDark=0.5;
float maskLight=1.5;

// Falloff shape.
// 1.0  = exp(x)
// 1.25 = in between
// 2.0  = gaussian
// 3.0  = more square
float shape=3.0;

// Amp signal.
float overdrive=1.0;

//------------------------------------------------------------------------

// sRGB to Linear.
// Assuing using sRGB typed textures this should not be needed.
float ToLinear1(float c){return(c<=0.04045)?c/12.92:pow((c+0.055)/1.055,2.4);}
vec3 ToLinear(vec3 c){return vec3(ToLinear1(c.r),ToLinear1(c.g),ToLinear1(c.b));}

// Linear to sRGB.
// Assuing using sRGB typed textures this should not be needed.
float ToSrgb1(float c){return(c<0.0031308?c*12.92:1.055*pow(c,0.41666)-0.055);}
vec3 ToSrgb(vec3 c){return vec3(ToSrgb1(c.r),ToSrgb1(c.g),ToSrgb1(c.b));}

// Testing only, something to help generate a dark signal for bloom test.
// Set to zero, or remove Test() if using this shader.
#if 0
 vec3 Test(vec3 c){return c*(1.0/64.0)+c*c;}
#else
 vec3 Test(vec3 c){return c;}
#endif

// Nearest emulated sample given floating point position and texel offset.
// Also zero's off screen.
vec3 Fetch(vec2 pos,vec2 off){
    pos=floor(pos*res+off)/res;
    if(max(abs(pos.x-0.5),abs(pos.y-0.5))>0.5)return vec3(0.0,0.0,0.0);
    return Test(ToLinear(texture(u_texture,pos,-16.0).rgb));}

// Distance in emulated pixels to nearest texel.
vec2 Dist(vec2 pos){pos=pos*res;return -((pos-floor(pos))-vec2(0.5));}

// Try different filter kernels.
float Gaus(float pos,float scale){return 0.333;}//exp2(scale*pow(abs(pos),shape));}

// 3-tap Gaussian filter along horz line.
vec3 Horz3(vec2 pos,float off){
    vec3 b=Fetch(pos,vec2(-1.0,off));
    vec3 c=Fetch(pos,vec2( 0.0,off));
    vec3 d=Fetch(pos,vec2( 1.0,off));
    float dst=Dist(pos).x;
    // Convert distance to weight.
    float scale=hardPix;
    float wb=Gaus(dst-1.0,scale);
    float wc=Gaus(dst+0.0,scale);
    float wd=Gaus(dst+1.0,scale);
    // Return filtered sample.
    return (b*wb+c*wc+d*wd)/(wb+wc+wd);}

// 5-tap Gaussian filter along horz line.
vec3 Horz5(vec2 pos,float off){
    vec3 a=Fetch(pos,vec2(-2.0,off));
    vec3 b=Fetch(pos,vec2(-1.0,off));
    vec3 c=Fetch(pos,vec2( 0.0,off));
    vec3 d=Fetch(pos,vec2( 1.0,off));
    vec3 e=Fetch(pos,vec2( 2.0,off));
    float dst=Dist(pos).x;
    // Convert distance to weight.
    float scale=hardPix;
    float wa=Gaus(dst-2.0,scale);
    float wb=Gaus(dst-1.0,scale);
    float wc=Gaus(dst+0.0,scale);
    float wd=Gaus(dst+1.0,scale);
    float we=Gaus(dst+2.0,scale);
    // Return filtered sample.
    return (a*wa+b*wb+c*wc+d*wd+e*we)/(wa+wb+wc+wd+we);}

// 7-tap Gaussian filter along horz line.
vec3 Horz7(vec2 pos,float off){
    vec3 a=Fetch(pos,vec2(-3.0,off));
    vec3 b=Fetch(pos,vec2(-2.0,off));
    vec3 c=Fetch(pos,vec2(-1.0,off));
    vec3 d=Fetch(pos,vec2( 0.0,off));
    vec3 e=Fetch(pos,vec2( 1.0,off));
    vec3 f=Fetch(pos,vec2( 2.0,off));
    vec3 g=Fetch(pos,vec2( 3.0,off));
    float dst=Dist(pos).x;
    // Convert distance to weight.
    float scale=hardPix;
    float wa=Gaus(dst-3.0,scale);
    float wb=Gaus(dst-2.0,scale);
    float wc=Gaus(dst-1.0,scale);
    float wd=Gaus(dst+0.0,scale);
    float we=Gaus(dst+1.0,scale);
    float wf=Gaus(dst+2.0,scale);
    float wg=Gaus(dst+3.0,scale);
    // Return filtered sample.
    return (a*wa+b*wb+c*wc+d*wd+e*we+f*wf+g*wg)/(wa+wb+wc+wd+we+wf+wg);}

// Return scanline weight.
float Scan(vec2 pos,float off){
    float dst=Dist(pos).y;
    return Gaus(dst+off,hardScan);}

// Allow nearest three lines to effect pixel.
vec3 Tri(vec2 pos){
    vec3 a=Horz5(pos,-2.0);
    vec3 b=Horz7(pos,-1.0);
    vec3 c=Horz7(pos, 0.0);
    vec3 d=Horz7(pos, 1.0);
    vec3 e=Horz5(pos, 2.0);
    float wa=Scan(pos,-2.0);
    float wb=Scan(pos,-1.0);
    float wc=Scan(pos, 0.0);
    float wd=Scan(pos, 1.0);
    float we=Scan(pos, 2.0);
    return (a*wa+b*wb+c*wc+d*wd+e*we)*overdrive;}

// Distortion of scanlines, and end of screen alpha.
vec2 Warp(vec2 pos){
    pos=pos*2.0-1.0;
    pos*=vec2(1.0+(pos.y*pos.y)*warp.x,1.0+(pos.x*pos.x)*warp.y);
    return pos*0.5+0.5;}

#if 0
// Very compressed TV style shadow mask.
vec3 Mask(vec2 pos){
    float line=maskLight;
    float odd=0.0;
    if(fract(pos.x/6.0)<0.5)odd=1.0;
    if(fract((pos.y+odd)/2.0)<0.5)line=maskDark;
    pos.x=fract(pos.x/3.0);
    vec3 mask=vec3(maskDark,maskDark,maskDark);
    if(pos.x<0.333)mask.r=maskLight;
    else if(pos.x<0.666)mask.g=maskLight;
    else mask.b=maskLight;
    mask*=line;
    return mask;}
#endif

#if 0
// Aperture-grille.
vec3 Mask(vec2 pos){
    pos.x=fract(pos.x/3.0);
    vec3 mask=vec3(maskDark,maskDark,maskDark);
    if(pos.x<0.333)mask.r=maskLight;
    else if(pos.x<0.666)mask.g=maskLight;
    else mask.b=maskLight;
    return mask;}
#endif

#if 0
// Stretched VGA style shadow mask (same as prior shaders).
vec3 Mask(vec2 pos){
    pos.x+=pos.y*3.0;
    vec3 mask=vec3(maskDark,maskDark,maskDark);
    pos.x=fract(pos.x/6.0);
    if(pos.x<0.333)mask.r=maskLight;
    else if(pos.x<0.666)mask.g=maskLight;
    else mask.b=maskLight;
    return mask;}
#endif

#if 1
// VGA style shadow mask.
vec3 Mask(vec2 pos){
    pos.xy=floor(pos.xy*vec2(1.0,0.5));
    pos.x+=pos.y*3.0;
    vec3 mask=vec3(maskDark,maskDark,maskDark);
    pos.x=fract(pos.x/6.0);
    if(pos.x<0.333)mask.r=maskLight;
    else if(pos.x<0.666)mask.g=maskLight;
    else mask.b=maskLight;
    return mask;}
#endif


// Draw dividing bars.
float Bar(float pos,float bar){pos-=bar;return pos*pos<4.0?0.0:1.0;}

// Entry.
vec4 crtColor(vec2 uv ){
    vec2 fragCoord = uv.xy * u_resolution.xy;
    vec2 pos=Warp(uv);
    vec4 fragColor = vec4(0.);
    fragColor.rgb=Tri(pos)*Mask(fragCoord.xy);
    fragColor.a=1.0;
    fragColor.rgb=ToSrgb(fragColor.rgb);
    return fragColor;
}




vec4 draw(sampler2D image, vec2 uv) {
    return texture2D(image,vec2(uv.x, uv.y));
}
float rand(vec2 co){
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}
vec4 blur(vec2 uv, sampler2D image, float blurAmount){
    float alpha = texture2D(image, uv).a;
    vec4 blurredImage = vec4(0.);
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
    return blurredImage / repeats;
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

vec4 colorFromBounds(sampler2D tex, vec2 uv, vec4 bounds, float reflectionColorFactor, bool applyCrt) {
/**    bool inTheCorner = uv.x < bounds.x + smallCornerR && uv.y < bounds.y + smallCornerR;
    bool validCorner = uv.x > bounds.x && uv.y > bounds.y;
    if (inTheCorner && !validCorner) {
        uv = revolved(uv, bounds.xy + smallCornerR);
    }*/
    //vec2 warpedUv = Warp(uv);
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
        //withReflections = Warp(withReflections);
        return blur(withReflections, tex, 0.08) * factor;
        //return blur13(tex, withReflections, virtualResolution, blurDirection) * factor;
    }


    return applyCrt ? crtColor(withReflections) : texture2D(tex, withReflections);
}


void main() {
/**    if (true) {
        gl_FragColor = texture2D(u_texture, Warp(v_texCoords));
        return;
    }*/
    vec4 gameColor = v_color * colorFromBounds(u_texture, v_texCoords, gameBounds, gameReflectionColorFactor, true);
    vec4 overlayColor = v_color * colorFromBounds(u_overlayTexture, v_texCoords, overlayBounds, ghostReflectionColorFactor, false);
    //vec4 color = (gameColor + overlayColor) / 2;
    gl_FragColor = gameColor + overlayColor;
    //gl_FragColor = v_color * crtColor(v_texCoords);
    //gl_FragColor = texture2D(u_texture, v_texCoords);
}
