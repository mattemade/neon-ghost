package com.game.template

import com.littlekt.Context
import com.littlekt.file.vfs.readAudioClipEx
import com.littlekt.file.vfs.readAudioStreamEx
import com.littlekt.file.vfs.readTiledMap
import com.littlekt.graphics.Textures
import com.littlekt.graphics.g2d.ParticleSimulator
import io.itch.mattemade.utils.asset.AssetPack

class Assets(context: Context) : AssetPack(context) {
    val music = Music(context).packed()
    val sound = Sound(context).packed()
    val level = Levels(context).packed()
    val normalReiAnimations = ReiAnimations.Normal(context, ::println).packed()
    val magicalReiAnimations = ReiAnimations.Magical(context, ::println).packed()
    val objects = Objects(context).packed()
}

class Sound(context: Context) : AssetPack(context) {
    val wind by prepare { context.resourcesVfs["sound/wind.mp3"].readAudioClipEx() }
}

class Music(context: Context) : AssetPack(context) {
    val background by prepare { context.resourcesVfs["sound/untitled.mp3"].readAudioStreamEx() }
}

class Levels(context: Context) : AssetPack(context) {
    val testRoom by prepare { context.resourcesVfs["level/level.tmj"].readTiledMap() }
    //val testRoom by prepare { context.resourcesVfs["brick-and-concrete/test-room.tmj"].readTiledMap() }
}

sealed class ReiAnimations(context: Context, mode: String, callback: (String) -> Unit) :
    AssetPack(context, callback) {
    //val idle by "texture/player/idle".prepareAnimationPlayer()
    //val idle by "texture/sailor/idle".prepareAnimationPlayer()
    val walk by "texture/rei/$mode/walk".prepareAnimationPlayer()
    val idle by "texture/rei/$mode/idle".prepareAnimationPlayer()
    val leftPunch by "texture/rei/$mode/left_punch".prepareAnimationPlayer()
    val quickLeftPunch by "texture/rei/$mode/quick_left_punch".prepareAnimationPlayer()
    val rightPunch by "texture/rei/$mode/right_punch".prepareAnimationPlayer()
    val quickRightPunch by "texture/rei/$mode/quick_right_punch".prepareAnimationPlayer()

    class Normal(context: Context, callback: (String) -> Unit) :
        ReiAnimations(context, "normal", callback)

    class Magical(context: Context, callback: (String) -> Unit) :
        ReiAnimations(context, "magical", callback)
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