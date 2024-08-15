package io.itch.mattemade.utils.atlas

import co.touchlab.stately.collections.ConcurrentMutableList
import co.touchlab.stately.collections.ConcurrentMutableMap
import com.littlekt.Context
import com.littlekt.file.Vfs
import com.littlekt.file.vfs.normalize
import com.littlekt.file.vfs.pathInfo
import com.littlekt.file.vfs.readTexture
import com.littlekt.graphics.FrameBuffer
import com.littlekt.graphics.Texture
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.graphics.g2d.TextureSlice
import com.littlekt.graphics.gl.TexMagFilter
import com.littlekt.graphics.gl.TexMinFilter
import com.littlekt.util.Scaler
import com.littlekt.util.viewport.ScalingViewport
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlin.math.max

class RuntimePacker(private val context: Context) : Releasing by Self() {

    private val batch = SpriteBatch(context)
    private val frameBuffer = FrameBuffer(
        4096,
        4096,
        listOf(
            FrameBuffer.TextureAttachment(
                minFilter = TexMinFilter.NEAREST,
                magFilter = TexMagFilter.NEAREST
            )
        )
    ).releasing().also { it.prepare(context) }
    private val frameBufferViewport = ScalingViewport(scaler = Scaler.None(), 4096, 4096).apply {
        update(4096, 4096, context)
    }
    private val frameBufferCamera = frameBufferViewport.camera
    private val frameBufferTexture: Texture by lazy { frameBuffer.textures[0] }
    private var currentX = 0
    private var currentY = 0
    private var nextY = 0
    private val slices = ConcurrentMutableMap<String, TextureSlice>()
    private val existingDeferreds = ConcurrentMutableMap<String, Deferred<TextureSlice>>()

    val queue = ConcurrentMutableList<TexturePlacement>()

    val isReady: Boolean
        get() = queue.isEmpty()

    fun exerciseQueue() {
        queue.block {
            val iterator = it.iterator()
            while (iterator.hasNext()) {
                val placement = iterator.next()
                frameBuffer.begin()
                frameBufferViewport.apply(context, true)
                batch.begin(frameBufferCamera.viewProjection)
                batch.draw(
                    placement.texture,
                    x = placement.x,
                    y = 4096f - placement.y - placement.texture.height,
                    originX = 0f,
                    originY = 0f,
                    width = placement.texture.width.toFloat(),
                    height = placement.texture.height.toFloat(),
                    flipY = true
                )
                batch.end()
                frameBuffer.end()
                placement.texture.release()
                iterator.remove()
            }
        }
    }

    suspend fun pack(texturePath: String, vfs: Vfs): Deferred<TextureSlice> {
        val normalizedPath = texturePath.pathInfo.normalize()
        return slices.block {
            val existingSlice = slices.get(normalizedPath)
            if (existingSlice != null) {
                return@block vfs.async { existingSlice }
            }
            val existingDeferred = existingDeferreds[normalizedPath]
            if (existingDeferred != null) {
                return@block existingDeferred
            }
            return@block vfs.async {
                val texture = vfs[normalizedPath].readTexture(
                    minFilter = TexMinFilter.NEAREST,
                    magFilter = TexMagFilter.NEAREST,
                    mipmaps = false
                )
                slices.block {
                    val width = texture.width
                    val height = texture.height
                    if (currentX + width > 4096) {
                        currentX = 0
                        currentY = nextY
                    }

                    if (currentY + height > 4096) {
                        error("Out of space!")
                    } else {
                        nextY = max(nextY, currentY + height)
                    }

                    queue.add(TexturePlacement(texture, currentX.toFloat(), currentY.toFloat()))

                    val slice = TextureSlice(
                        frameBufferTexture,
                        x = currentX,
                        y = currentY,
                        width = texture.width,
                        height = texture.height
                    )
                    currentX += width
                    slices.put(normalizedPath, slice)
                    slice
                }

            }.also { existingDeferreds[normalizedPath] = it }
        }
    }

    data class TexturePlacement(val texture: Texture, val x: Float, val y: Float)
}