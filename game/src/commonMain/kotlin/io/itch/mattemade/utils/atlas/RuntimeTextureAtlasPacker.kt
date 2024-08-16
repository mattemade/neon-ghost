package io.itch.mattemade.utils.atlas

import co.touchlab.stately.collections.ConcurrentMutableList
import co.touchlab.stately.collections.ConcurrentMutableMap
import com.littlekt.Context
import com.littlekt.file.Vfs
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

class RuntimeTextureAtlasPacker(context: Context) : Releasing by Self() {

    private val mutableTextureAtlas = MutableTextureAtlas(context)
    private val immutableTextureAtlas by lazy { mutableTextureAtlas.toImmutable(false).releasing() }
    private val slices = ConcurrentMutableMap<String, TextureSlice>()
    private val deferredSlices = ConcurrentMutableMap<String, Deferred<TextureSlice>>()

    private val queue = ConcurrentMutableList<TexturePlacement>()

    private var _isReady = false
    val isReady: Boolean
        get() = _isReady || queue.isEmpty().also { _isReady = it }

    fun packAtlas(): TextureAtlas {
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
        return immutableTextureAtlas
    }

    suspend fun pack(texturePath: String, vfs: Vfs): Deferred<TextureSlice> {
        val normalizedPath = texturePath.pathInfo.normalize()
        return slices.block {
            val existingSlice = slices.get(normalizedPath)
            if (existingSlice != null) {
                println("Found existing slice for $normalizedPath: ${existingSlice.hashCode()} $existingSlice")
                return@block vfs.async { existingSlice }
            }
            val existingDeferred = deferredSlices[normalizedPath]
            if (existingDeferred != null) {
                return@block existingDeferred
            }
            return@block vfs.async {
                val texture = vfs[normalizedPath].readTexture(
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
                //slices.block {
                queue.add(TexturePlacement(normalizedPath, texture))
                slices.put(normalizedPath, slice)
                slice
                //}

            }.also { this.deferredSlices[normalizedPath] = it }
        }
    }

    data class TexturePlacement(val name: String, val texture: Texture)
}