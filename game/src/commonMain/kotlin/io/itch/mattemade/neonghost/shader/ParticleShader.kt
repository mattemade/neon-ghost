package io.itch.mattemade.neonghost.shader

import com.littlekt.graphics.shader.FragmentShaderModel
import com.littlekt.graphics.shader.ShaderParameter
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.shader.VertexShaderModel

fun createParticleShader(vertex: String, fragment: String) =
    ShaderProgram(
        ParticleVertexShader(vertex),
        ParticleFragmentShader(fragment)
    )

class ParticleVertexShader(private val sourceText: String) : VertexShaderModel() {
    val uProjTrans = ShaderParameter.UniformMat4("u_projTrans")
    val uTime = ShaderParameter.UniformFloat("u_time")
    val uInterpolation = ShaderParameter.UniformInt("u_interpolation")

    override val parameters: LinkedHashSet<ShaderParameter> =
        linkedSetOf(uProjTrans, uInterpolation, uTime)

    override var source: String = sourceText
}

class ParticleFragmentShader(private val sourceText: String) : FragmentShaderModel() {


    override val parameters: LinkedHashSet<ShaderParameter> = linkedSetOf()

    override var source: String = sourceText
}