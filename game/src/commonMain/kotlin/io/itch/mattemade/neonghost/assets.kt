package io.itch.mattemade.neonghost

import com.littlekt.Context
import com.littlekt.PreparableGameAsset
import com.littlekt.Releasable
import com.littlekt.audio.AudioClipEx
import com.littlekt.audio.AudioStreamEx
import com.littlekt.file.vfs.baseName
import com.littlekt.file.vfs.pathInfo
import com.littlekt.file.vfs.readAudioClipEx
import com.littlekt.file.vfs.readAudioStreamEx
import com.littlekt.file.vfs.readTiledMap
import com.littlekt.graphics.Textures
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.TextureAtlas
import com.littlekt.graphics.g2d.TextureSlice
import com.littlekt.graphics.shader.FragmentShader
import com.littlekt.graphics.shader.ShaderProgram
import com.littlekt.graphics.shader.VertexShader
import io.itch.mattemade.neonghost.shader.createTestShader
import io.itch.mattemade.utils.asset.AssetPack
import io.itch.mattemade.utils.atlas.RuntimeTextureAtlasPacker

class Assets(context: Context, animationEventListener: (String) -> Unit) : AssetPack(context) {
    private val runtimeTextureAtlasPacker = RuntimeTextureAtlasPacker(context).releasing()
    private val tileSets by pack { TileSets(context, runtimeTextureAtlasPacker) }

    val texture by pack { Textures(context, runtimeTextureAtlasPacker) }
    val animation by pack { Animations(context, runtimeTextureAtlasPacker, animationEventListener) }
    val sound by pack { Sound(context) }
    val music by pack { Music(context) }
    val script by pack { Script(context) }
    val objects by pack { Objects(context) }
    val shader by pack { Shaders(context) }

    private val atlas by prepare(1) { runtimeTextureAtlasPacker.packAtlas() }

    val textDrawer by preparePlain {  }
    val level by pack(2) { Levels(context, atlas) }
}

class Script(context: Context): AssetPack(context) {
    val test1 by preparePlain { context.vfs["dialogue/test1.txt"].readLines() }
}

class Textures(context: Context, private val packer: RuntimeTextureAtlasPacker) :
    AssetPack(context) {
    private fun String.pack(): PreparableGameAsset<TextureSlice> =
        preparePlain { packer.pack(this).await() }

    val dialogueArrow by "texture/dialogue/arrow.png".pack()
    val white by "texture/misc/white.png".pack()
    val fontWhite by "texture/dialogue/font_white.png".pack()
}

class Sound(context: Context) : AssetPack(context) {
    //val wind by prepare { context.resourcesVfs["sound/untitled.mp3"].readAudioClipEx() }
    val speech1 by prepare { context.resourcesVfs["sound/speech1.mp3"].readAudioClipEx() }
}

class Music(context: Context) : AssetPack(context) {
    val background by prepare {
        context.resourcesVfs["sound/magical girl 3b.mp3"].readAudioClipEx().asTrack(150f, -0.1f)
        //context.resourcesVfs["sound/untitled.mp3"].readAudioStreamEx().bpm(138.6882f, -0.2f)
    }
    val background1c by prepare {
        context.resourcesVfs["sound/magical girl 1c.mp3"].readAudioClipEx().asTrack(129.97198f, -0.1f)
    }
}

data class StreamBpm(val stream: AudioClipEx, val bpm: Float, val offset: Float) : Releasable by stream

private fun AudioClipEx.asTrack(bpm: Float, offset: Float) = StreamBpm(this, bpm, offset)

class Levels(context: Context, atlas: TextureAtlas? = null) : AssetPack(context) {
    val testRoom by prepare {
        context.resourcesVfs["level/level.tmj"].readTiledMap(
            atlas,
            tilesetBorder = 0
        )
    }
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

class Animations(
    context: Context,
    runtimeTextureAtlasPacker: RuntimeTextureAtlasPacker, callback: (String) -> Unit
) : AssetPack(context, callback) {
    val normalReiAnimations by pack {
        CharacterAnimations(
            context,
            runtimeTextureAtlasPacker,
            "rei/normal",
            callback
        )
    }
    val magicalReiAnimations by pack {
        CharacterAnimations(
            context,
            runtimeTextureAtlasPacker,
            "rei/magical",
            callback
        )
    }
    val punkAnimations by pack {
        CharacterAnimations(
            context,
            runtimeTextureAtlasPacker,
            "punk",
            callback
        )
    }
    val guardAnimations by pack {
        CharacterAnimations(
            context,
            runtimeTextureAtlasPacker,
            "guard",
            callback
        )
    }
    val officerAnimations by pack {
        CharacterAnimations(
            context,
            runtimeTextureAtlasPacker,
            "officer",
            callback
        )
    }
    val ghostGrayAnimations by pack {
        GhostAnimations(context, "gray", runtimeTextureAtlasPacker)
    }
    /*    val ghostColorAnimations by pack {
            GhostAnimations(context, "color", runtimeTextureAtlasPacker)
        }*/
}

class CharacterAnimations(
    context: Context,
    runtimeTextureAtlasPacker: RuntimeTextureAtlasPacker,
    character: String,
    callback: (String) -> Unit
) :
    AssetPack(context, callback) {
    val walk by "texture/$character/walk".prepareAnimationPlayer(runtimeTextureAtlasPacker)
    val idle by "texture/$character/idle".prepareAnimationPlayer(runtimeTextureAtlasPacker)
    val hit by "texture/$character/hit".prepareAnimationPlayer(runtimeTextureAtlasPacker)
    val prepare by "texture/$character/prepare".prepareAnimationPlayer(runtimeTextureAtlasPacker)
    val leftPunch by "texture/$character/left_punch".prepareAnimationPlayer(
        runtimeTextureAtlasPacker
    )
    val rightPunch by "texture/$character/right_punch".prepareAnimationPlayer(
        runtimeTextureAtlasPacker
    )
}

class GhostAnimations(context: Context, type: String, packer: RuntimeTextureAtlasPacker) :
    AssetPack(context) {

    val idle by "texture/ghost/$type/idle".prepareAnimationPlayer(packer)
    val fly by "texture/ghost/$type/fly".prepareAnimationPlayer(packer)
    val cast by "texture/ghost/$type/cast".prepareAnimationPlayer(packer)
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

class Shaders(context: Context) : AssetPack(context) {
    private fun <V : VertexShader, F : FragmentShader> String.readShader(shaderFromSource: (String, String) -> ShaderProgram<V, F>): PreparableGameAsset<ShaderProgram<V, F>> =
        preparePlain {
            val vertex = context.resourcesVfs["shader/${this}.vert.glsl"].readString()
            val fragment = context.resourcesVfs["shader/${this}.frag.glsl"].readString()

            shaderFromSource(vertex, fragment).apply { prepare(context) }
        }

    val test by "test".readShader(::createTestShader)
}