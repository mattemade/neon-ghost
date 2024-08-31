package io.itch.mattemade.neonghost.shader.representation

import com.littlekt.Context
import com.littlekt.file.createFloatBuffer
import com.littlekt.graphics.GL

class BoundableBuffer(private val context: Context, private val content: FloatArray, private val elementSize: Int, private val instanced: Boolean = false) {

    private val floatBuffer = createFloatBuffer(content)
    private val buffer = context.gl.createBuffer().also {
        context.gl.bindBuffer(GL.ARRAY_BUFFER, it)
        context.gl.bufferData(GL.ARRAY_BUFFER, floatBuffer, GL.STATIC_DRAW)
    }
    val elements = content.size / elementSize

    fun bind(location: Int) {
        context.gl.bindBuffer(GL.ARRAY_BUFFER, buffer)
        context.gl.enableVertexAttribArray(location)
        context.gl.vertexAttribPointer(location, elementSize, GL.FLOAT, false, 0, 0)
        if (instanced) {
            context.gl.vertexAttribDivisor(location, 1)
        }
    }

}