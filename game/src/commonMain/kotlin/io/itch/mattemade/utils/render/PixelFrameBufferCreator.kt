package io.itch.mattemade.utils.render

import com.littlekt.Context
import com.littlekt.graphics.FrameBuffer
import com.littlekt.graphics.gl.TexMagFilter
import com.littlekt.graphics.gl.TexMinFilter

fun Context.createPixelFrameBuffer(width: Int, height: Int) =
    FrameBuffer(
        width,
        height,
        listOf(
            FrameBuffer.TextureAttachment(
                minFilter = TexMinFilter.NEAREST,
                magFilter = TexMagFilter.NEAREST
            )
        )
    ).also {
        it.prepare(this)
    }