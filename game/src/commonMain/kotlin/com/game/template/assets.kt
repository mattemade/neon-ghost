package com.game.template

import com.littlekt.Context
import com.littlekt.file.vfs.readAudioStreamEx
import com.littlekt.file.vfs.readTiledMap
import com.littlekt.graphics.Textures
import com.littlekt.graphics.g2d.ParticleSimulator
import io.itch.mattemade.utils.asset.AssetPack
import kotlin.time.Duration

class Assets(context: Context) : AssetPack(context) {
    val music = Music(context).packed()
    val level = Levels(context).packed()
    val playerAnimations = PlayerAnimations(context, ::println).packed()
    val objects = Objects(context).packed()
}

class Music(context: Context) : AssetPack(context) {
    val background by prepare { context.resourcesVfs["sound/untitled.mp3"].readAudioStreamEx() }
}

class Levels(context: Context) : AssetPack(context) {
    val testRoom by prepare { context.resourcesVfs["brick-and-concrete/test-room.tmj"].readTiledMap() }
}

class PlayerAnimations(context: Context, callback: (String) -> Unit) :
    AssetPack(context, callback) {
    val idle by "texture/player/idle".prepareAnimationPlayer()
}

class Objects(context: Context) : AssetPack(context) {
    val particleSimulator by preparePlain {
        ParticleSimulator(2048 / 2).also {
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