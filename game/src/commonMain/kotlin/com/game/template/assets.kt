package com.game.template

import com.littlekt.Context
import com.littlekt.PreparableGameAsset
import com.littlekt.file.vfs.baseName
import com.littlekt.file.vfs.pathInfo
import com.littlekt.file.vfs.readAudioClipEx
import com.littlekt.file.vfs.readAudioStreamEx
import com.littlekt.file.vfs.readTiledMap
import com.littlekt.graphics.Textures
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.TextureAtlas
import com.littlekt.graphics.g2d.TextureSlice
import io.itch.mattemade.utils.asset.AssetPack
import io.itch.mattemade.utils.atlas.RuntimeTextureAtlasPacker

class Assets(context: Context, animationEventListener: (String) -> Unit) : AssetPack(context) {
    val runtimeTextureAtlasPacker = RuntimeTextureAtlasPacker(context).releasing()

    val normalReiAnimations by pack {
        ReiAnimations.Normal(
            context,
            runtimeTextureAtlasPacker,
            animationEventListener
        )
    }
    val magicalReiAnimations by pack {
        ReiAnimations.Magical(
            context,
            runtimeTextureAtlasPacker,
            animationEventListener
        )
    }

    val music by pack { Music(context) }
    //val sound by pack { Sound(context) }
    val objects by pack { Objects(context) }
    val tileSets by pack { TileSets(context, runtimeTextureAtlasPacker) }

    val atlas by prepare(1) { runtimeTextureAtlasPacker.packAtlas() }

    val level by pack(2) { Levels(context, atlas) }
}

class Sound(context: Context) : AssetPack(context) {
    val wind by prepare { context.resourcesVfs["sound/untitled.mp3"].readAudioClipEx() }
}

class Music(context: Context) : AssetPack(context) {
    val background by prepare { context.resourcesVfs["sound/untitled.mp3"].readAudioStreamEx() }
}

class Levels(context: Context, atlas: TextureAtlas? = null) : AssetPack(context) {
    val testRoom by prepare { context.resourcesVfs["level/level.tmj"].readTiledMap(atlas, tilesetBorder = 0) }
    //val testRoom by prepare { context.resourcesVfs["brick-and-concrete/test-room.tmj"].readTiledMap() }
}

class TileSets(context: Context, private val packer: RuntimeTextureAtlasPacker) :
    AssetPack(context) {
    private fun String.pack(): PreparableGameAsset<TextureSlice> =
        preparePlain { packer.pack(this, pathInfo.baseName).await() }
    val wall by "level/Outside-Wall 48_48.png".pack()
    val light by "level/Outside-Light 150_150.png".pack()
    val window by "level/Outside-Window 48_48.png".pack()
}

sealed class ReiAnimations(
    context: Context,
    runtimeTextureAtlasPacker: RuntimeTextureAtlasPacker,
    mode: String,
    callback: (String) -> Unit
) :
    AssetPack(context, callback) {
    //val idle by "texture/player/idle".prepareAnimationPlayer()
    //val idle by "texture/sailor/idle".prepareAnimationPlayer()
    val walk by "texture/rei/$mode/walk".prepareAnimationPlayer(runtimeTextureAtlasPacker)
    val idle by "texture/rei/$mode/idle".prepareAnimationPlayer(runtimeTextureAtlasPacker)
    val leftPunch by "texture/rei/$mode/left_punch".prepareAnimationPlayer(runtimeTextureAtlasPacker)
    val quickLeftPunch by "texture/rei/$mode/quick_left_punch".prepareAnimationPlayer(
        runtimeTextureAtlasPacker
    )
    val rightPunch by "texture/rei/$mode/right_punch".prepareAnimationPlayer(
        runtimeTextureAtlasPacker
    )
    val quickRightPunch by "texture/rei/$mode/quick_right_punch".prepareAnimationPlayer(
        runtimeTextureAtlasPacker
    )

    class Normal(
        context: Context,
        runtimeTextureAtlasPacker: RuntimeTextureAtlasPacker,
        callback: (String) -> Unit
    ) :
        ReiAnimations(context, runtimeTextureAtlasPacker, "normal", callback)

    class Magical(
        context: Context,
        runtimeTextureAtlasPacker: RuntimeTextureAtlasPacker,
        callback: (String) -> Unit
    ) :
        ReiAnimations(context, runtimeTextureAtlasPacker, "magical", callback)
}


class Objects(context: Context) : AssetPack(context) {
    val particleSimulator by preparePlain {
        ParticleSimulator(2048 * 3 / 2).also {
            // pre-allocate all the particles ahead of time to avoid stuttering
            val size = it.particles.size
            for (i in 0 until size) {
                it.alloc(Textures.transparent, 0f, 0f).apply {
                    //life = Duration.ZERO
                }
            }
        }
    }
}