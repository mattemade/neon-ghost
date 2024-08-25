package io.itch.mattemade.neonghost

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.littlekt.Context
import com.littlekt.PreparableGameAsset
import com.littlekt.Releasable
import com.littlekt.audio.AudioClipEx
import com.littlekt.file.vfs.baseName
import com.littlekt.file.vfs.pathInfo
import com.littlekt.file.vfs.readAudioClipEx
import com.littlekt.file.vfs.readTiledMap
import com.littlekt.graphics.Textures
import com.littlekt.graphics.g2d.ParticleSimulator
import com.littlekt.graphics.g2d.TextureAtlas
import com.littlekt.graphics.g2d.TextureSlice
import com.littlekt.graphics.g2d.tilemap.tiled.TiledMap
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
    val objects by pack { Objects(context) }
    val shader by pack { Shaders(context) }

    private val atlas by prepare(1) { runtimeTextureAtlasPacker.packAtlas() }

    val level by pack(2) { Levels(context, atlas) }
}

class Textures(context: Context, private val packer: RuntimeTextureAtlasPacker) :
    AssetPack(context) {
    private fun String.pack(): PreparableGameAsset<TextureSlice> =
        preparePlain { packer.pack(this).await() }

    val dialogueArrow by "texture/dialogue/arrow.png".pack()
    val white by "texture/misc/white.png".pack()
    val fontWhite by "texture/dialogue/font_white.png".pack()
    val portraits by preparePlain {
        listOf("rei", "terminal", "punk").associateWith {
            packer.pack("texture/portrait/$it.png").await()
        }
    }
}

class Sound(context: Context) : AssetPack(context) {
    //val wind by prepare { context.resourcesVfs["sound/untitled.mp3"].readAudioClipEx() }
    val speech1 by prepare { context.resourcesVfs["sound/speech1.mp3"].readAudioClipEx() }
}

class Music(context: Context) : AssetPack(context) {

    private fun String.mp3name(): String = this.substringAfterLast("/").substringBefore(".mp3")
    val concurrentTracks = ConcurrentMutableMap<String, StreamBpm>()

    private val preparations = listOf(
        "sound/magical girl 3d.mp3" to 150f,
        "sound/magical girl 1c.mp3" to 129.97198f,
    ).forEach {
        val name = it.first.mp3name()
        prepare {
            val track = context.resourcesVfs[it.first].readAudioClipEx().asTrack(name, it.second, 0.0f)
            concurrentTracks[name] = track
            track
        }
    }
}

data class StreamBpm(val name: String, val stream: AudioClipEx, val bpm: Float, val offset: Float) :
    Releasable by stream

private fun AudioClipEx.asTrack(name: String, bpm: Float, offset: Float) = StreamBpm(name, this, bpm, offset)

class Levels(context: Context, private val atlas: TextureAtlas? = null) : AssetPack(context) {
    private fun String.pack(): PreparableGameAsset<TiledMap> =
        prepare {
            context.resourcesVfs[this].readTiledMap(atlas, tilesetBorder = 0)
        }

    private fun String.levelName(): String = this.substringAfterLast("/").substringBefore(".tmj")

    //val startLevel by "level/boxing_club.tmj".pack()
    val levels by preparePlain {
        listOf(
            "level/boxing_club.tmj" to false,
            "level/going_home.tmj" to false,
            "level/rei_home.tmj" to false,
            "level/strange_room.tmj" to false,
            "level/street_level.tmj" to false,
            "level/street_back_alley.tmj" to false,
            "level/interrogation_room.tmj" to true,
            "level/washing_room.tmj" to true,
            "level/security_lift.tmj" to true,
            "level/main_lift.tmj" to true,
            "level/power_plant.tmj" to true,
            "level/roof.tmj" to true,
            "level/research_inner_room.tmj" to true,
            "level/research_outer_room.tmj" to true,
            "level/control_room.tmj" to true,
            "level/lobby_room.tmj" to true,
            "level/corridor.tmj" to true,
        ).associate {
            val level = context.resourcesVfs[it.first].readTiledMap(atlas, tilesetBorder = 0)
                .releasing()
            it.first.levelName() to LevelSpec(level, it.second)
        }
    }
}

class LevelSpec(val level: TiledMap, val freeCameraY: Boolean)

class TileSets(context: Context, private val packer: RuntimeTextureAtlasPacker) :
    AssetPack(context) {
    private fun String.pack(): PreparableGameAsset<TextureSlice> =
        preparePlain { packer.pack(this, pathInfo.baseName).await() }

    val wall by "level/Outside-Wall 48_48.png".pack()
    val light by "level/Outside-Light 150_150.png".pack()
    val window by "level/Outside-Window 48_48.png".pack()
    val terminalTile by "level/terminal_tile.png".pack()
    val interior by "level/PV_Tides_Asset_Pack/tilesets/interior modern/Interior A4.png".pack()
    val abstract by "level/abstract.png".pack()
    val metalWall by "level/tilesets/0_Asset_Wall_144144.png".pack()
    val metalFloor by "level/tilesets/0_Asset_Floor_4848.png".pack()
    val controls by "level/tilesets/0_Asset_Control_9696.png".pack()
    val container by "level/tilesets/0_Asset_Container_240240.png".pack()
    val chair1 by "level/tilesets/0_Asset_Chair_240240.png".pack()
    val chair2 by "level/tilesets/0_Asset_Chair02_240240.png".pack()
    val doorTransparent by "level/tilesets/door_transparent.png".pack()
    val redDoor by "level/tilesets/red_door.png".pack()
    val redDoorTransparent by "level/tilesets/red_door_transparent.png".pack()
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