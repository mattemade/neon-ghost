package com.game.template.shader

import com.littlekt.graphics.Texture
import com.littlekt.graphics.shader.SpriteShader
import com.littlekt.graphics.webgpu.BindGroup
import com.littlekt.graphics.webgpu.BindGroupDescriptor
import com.littlekt.graphics.webgpu.BindGroupEntry
import com.littlekt.graphics.webgpu.BindGroupLayoutDescriptor
import com.littlekt.graphics.webgpu.BindGroupLayoutEntry
import com.littlekt.graphics.webgpu.BufferBindingLayout
import com.littlekt.graphics.webgpu.Device
import com.littlekt.graphics.webgpu.RenderPassEncoder
import com.littlekt.graphics.webgpu.SamplerBindingLayout
import com.littlekt.graphics.webgpu.ShaderStage
import com.littlekt.graphics.webgpu.TextureBindingLayout
import com.littlekt.util.align


/*    vec2 uv=fragCoord/iResolution.xy;

    //curving
    vec2 crtUV=uv*2.-1.;
    vec2 offset=crtUV.yx/CURVATURE;
    crtUV+=crtUV*offset*offset;
    crtUV=crtUV*.5+.5;

    vec2 edge=smoothstep(0., BLUR, crtUV)*(1.-smoothstep(1.-BLUR, 1., crtUV));

    //chromatic abberation
    fragColor.rgb=vec3(
        texture(iChannel0, (crtUV-.5)*CA_AMT+.5).r,
        texture(iChannel0, crtUV).g,
        texture(iChannel0, (crtUV-.5)/CA_AMT+.5).b
    )*edge.x*edge.y;

    //lines
    if(mod(fragCoord.y, 2.)<1.) fragColor.rgb*=.7;
    else if(mod(fragCoord.x, 3.)<1.) fragColor.rgb*=.7;
    else fragColor*=1.2;*/
class Crt(device: Device, cameraDynamicSize: Int = 50) :
    SpriteShader(
        device,
        // language=wgsl
        src =
        """
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
        
        const warp: f32 = 0.75;
        const scan: f32 = 0.75;
        const CURVATURE: f32 = 4.2;
        const BLUR: f32 = .021;
        const CA_AMT: f32 = 1.024;

        @fragment
        fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
            var crtUV: vec2<f32> = in.uv*2. - 1.;
            let offset: vec2<f32> = crtUV.yx / CURVATURE;
            crtUV = crtUV + (crtUV * offset * offset);
            crtUV = crtUV * 0.5 + 0.5;
            let edge: vec2<f32> = smoothstep(vec2f(0.), vec2f(BLUR), crtUV) * (1. - smoothstep(vec2f(1. - BLUR), vec2f(1.), crtUV));
            var colorRgb = vec3<f32>(
                    textureSample(my_texture, my_sampler, (crtUV - 0.5) * CA_AMT + 0.5).r,
                    textureSample(my_texture, my_sampler, crtUV).g,
                    textureSample(my_texture, my_sampler, (crtUV - 0.5) / CA_AMT + 0.5).b
                ) * edge.x * edge.y;
            if ((in.uv.y % 2.) < 1.) {
                colorRgb *= .7;
            } else if ((in.uv.x % 3.) < 1.) {
                colorRgb *= .7;
            } else {
                colorRgb *= 1.2;            
            }
            return vec4f(colorRgb, 1.);
            //return textureSample(my_texture, my_sampler, in.uv) * in.color;
        }
        """
            .trimIndent(),
        layout =
        listOf(
            BindGroupLayoutDescriptor(
                listOf(
                    BindGroupLayoutEntry(
                        0,
                        ShaderStage.VERTEX,
                        BufferBindingLayout(
                            hasDynamicOffset = true,
                            minBindingSize =
                            (Float.SIZE_BYTES * 16)
                                .align(device.limits.minUniformBufferOffsetAlignment)
                                .toLong()
                        )
                    )
                )
            ),
            BindGroupLayoutDescriptor(
                listOf(
                    BindGroupLayoutEntry(0, ShaderStage.FRAGMENT, TextureBindingLayout()),
                    BindGroupLayoutEntry(1, ShaderStage.FRAGMENT, SamplerBindingLayout())
                )
            )
        ),
        cameraDynamicSize = cameraDynamicSize
    ) {

    override fun MutableList<BindGroup>.createBindGroupsWithTexture(
        texture: Texture,
        data: Map<String, Any>
    ) {
        add(
            device.createBindGroup(
                BindGroupDescriptor(
                    layouts[0],
                    listOf(BindGroupEntry(0, cameraUniformBufferBinding))
                )
            )
        )
        add(
            device.createBindGroup(
                BindGroupDescriptor(
                    layouts[1],
                    listOf(BindGroupEntry(0, texture.view), BindGroupEntry(1, texture.sampler))
                )
            )
        )
    }

    override fun setBindGroups(
        encoder: RenderPassEncoder,
        bindGroups: List<BindGroup>,
        dynamicOffsets: List<Long>
    ) {
        encoder.setBindGroup(0, bindGroups[0], dynamicOffsets)
        encoder.setBindGroup(1, bindGroups[1])
    }
}