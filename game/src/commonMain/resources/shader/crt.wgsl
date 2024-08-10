struct CameraUniform {
    view_proj: mat4x4<f32>
};
@group(0) @binding(0)
var<uniform> camera: CameraUniform;

struct VertexOutput {
    @location(0) color: vec4<f32>,
    @location(1) uv: vec2<f32>,
    @builtin(position) position: vec4<f32>,
};

@vertex
fn vs_main(
    @location(0) pos: vec3<f32>,
    @location(1) color: vec4<f32>,
    @location(2) uvs: vec2<f32>) -> VertexOutput {

    var output: VertexOutput;
    output.position = camera.view_proj * vec4<f32>(pos.x, pos.y, pos.z, 1);
    output.color = color;
    output.uv = uvs;

    return output;
}

@group(1) @binding(0)
var my_texture: texture_2d<f32>;
@group(1) @binding(1)
var my_sampler: sampler;

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    return textureSample(my_texture, my_sampler, in.uv) * in.color;
}
