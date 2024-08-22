package io.itch.mattemade.utils.atlas

import co.touchlab.stately.collections.ConcurrentMutableList
import co.touchlab.stately.collections.ConcurrentMutableMap
import com.littlekt.Context
import com.littlekt.file.vfs.normalize
import com.littlekt.file.vfs.pathInfo
import com.littlekt.file.vfs.readTexture
import com.littlekt.graphics.Texture
import com.littlekt.graphics.g2d.TextureAtlas
import com.littlekt.graphics.g2d.TextureSlice
import com.littlekt.graphics.gl.TexMagFilter
import com.littlekt.graphics.gl.TexMinFilter
import com.littlekt.graphics.slice
import com.littlekt.util.MutableTextureAtlas
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

class RuntimeTextureAtlasPacker(private val context: Context) : Releasing by Self() {

    private val mutableTextureAtlas = MutableTextureAtlas(context)
    private val immutableTextureAtlas by lazy { mutableTextureAtlas.toImmutable(false).releasing() }
    private val slices = ConcurrentMutableMap<String, TextureSlice>()
    private val deferredSlices = ConcurrentMutableMap<String, Deferred<TextureSlice>>()

    private val queue = ConcurrentMutableList<TexturePlacement>()

    var isReady: Boolean = false
        private set

    fun packAtlas(): TextureAtlas {
        if (isReady) {
            return immutableTextureAtlas
        }
        queue.block { queueEntry ->
            slices.block {
                queueEntry.forEach {
                    mutableTextureAtlas.add(it.texture.slice(), it.name)
                }
                immutableTextureAtlas.entries.forEach { entry ->
                    slices[entry.name]?.apply {
                        setSlice(entry.slice)
                    }
                }
                queue.forEach { it.texture.release() }
                queue.clear()
            }
        }
        isReady = true
        return immutableTextureAtlas
    }

    suspend fun pack(
        texturePath: String,
        normalizedPath: String = texturePath.pathInfo.normalize()
    ): Deferred<TextureSlice> =
        slices.block {
            val existingSlice = slices.get(normalizedPath)
            if (existingSlice != null) {
                return@block context.vfs.async { existingSlice }
            }
            val existingDeferred = deferredSlices[normalizedPath]
            if (existingDeferred != null) {
                return@block existingDeferred
            }
            return@block context.vfs.async {
                val texture = context.vfs[texturePath].readTexture(
                    minFilter = TexMinFilter.NEAREST,
                    magFilter = TexMagFilter.NEAREST,
                    mipmaps = false
                )
                val slice = TextureSlice(
                    texture,
                    x = 0,
                    y = 0,
                    width = texture.width,
                    height = texture.height
                )
                queue.add(TexturePlacement(normalizedPath, texture))
                slices.put(normalizedPath, slice)
                slice

            }.also { this.deferredSlices[normalizedPath] = it }
        }

    data class TexturePlacement(val name: String, val texture: Texture)
}