package io.itch.mattemade.neonghost.shader

import com.littlekt.graphics.shader.FragmentShaderModel
import com.littlekt.graphics.shader.ShaderParameter
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.shader.VertexShaderModel

fun createCabinetShader(vertex: String, fragment: String) =
    ShaderProgram(
        CabinetVertexShader(vertex),
        CabinetFragmentShader(fragment)
    )


class CabinetVertexShader(private val sourceText: String) : VertexShaderModel() {
    val uProjTrans = ShaderParameter.UniformMat4("u_projTrans")
    val uTextureSizes = ShaderParameter.UniformVec4("u_textureSizes")
    val uSampleProperties = ShaderParameter.UniformVec4("u_sampleProperties")
    val aPosition = ShaderParameter.Attribute("a_position")
    val aColor = ShaderParameter.Attribute("a_color")
    val aTexCoord0 = ShaderParameter.Attribute("a_texCoord0")

    override val parameters: LinkedHashSet<ShaderParameter> =
        linkedSetOf(uProjTrans, uTextureSizes, uSampleProperties, aPosition, aColor, aTexCoord0)

    override var source: String = sourceText
}

class CabinetFragmentShader(private val sourceText: String) : FragmentShaderModel() {

    val uTexture = ShaderParameter.UniformSample2D("u_texture")
    //val uGameTexture = ShaderParameter.UniformSample2D("u_gameTexture")
    val uOverlayTexture = ShaderParameter.UniformSample2D("u_overlayTexture")
    val uTextureSizes = ShaderParameter.UniformVec4("u_textureSizes")
    val uSampleProperties = ShaderParameter.UniformVec4("u_sampleProperties")
    val uResolution = ShaderParameter.UniformVec2("u_resolution")
    val uTime = ShaderParameter.UniformFloat("u_time")
    val uScale = ShaderParameter.UniformFloat("u_scale")

    override val parameters: LinkedHashSet<ShaderParameter> = linkedSetOf(
        uTexture, /*uGameTexture, */
        uOverlayTexture,
        uTextureSizes,
        uSampleProperties,
        uResolution,
        uTime,
        uScale,
    )

    override var source: String = sourceText
}