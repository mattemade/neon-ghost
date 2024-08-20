package io.itch.mattemade.neonghost.shader

import com.littlekt.graphics.shader.FragmentShaderModel
import com.littlekt.graphics.shader.ShaderParameter
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.shader.VertexShaderModel

fun createTestShader(vertex: String, fragment: String) =
    ShaderProgram(
        TestVertexShader(vertex),
        TestFragmentShader(fragment)
    )

class TestVertexShader(private val sourceText: String) : VertexShaderModel() {
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

class TestFragmentShader(private val sourceText: String) : FragmentShaderModel() {

    val uTexture = ShaderParameter.UniformSample2D("u_texture")
    val uTextureSizes = ShaderParameter.UniformVec4("u_textureSizes")
    val uSampleProperties = ShaderParameter.UniformVec4("u_sampleProperties")

    override val parameters: LinkedHashSet<ShaderParameter> = linkedSetOf(uTexture, uTextureSizes, uSampleProperties)

    override var source: String = sourceText
}