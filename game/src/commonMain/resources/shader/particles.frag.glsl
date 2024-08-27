uniform sampler2D u_texture;
uniform vec4 u_textureSizes;
uniform vec4 u_sampleProperties;
varying lowp vec4 v_color;
varying vec2 v_texCoords;

void main()
{
    vec4 color = v_color * texture2D(u_texture, v_texCoords);
    gl_FragColor = color;
}
