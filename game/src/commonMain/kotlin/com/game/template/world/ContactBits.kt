package com.game.template.world

class ContactBits {
    companion object {
        const val REI = 0x0001
        const val ENEMY = 0x0002
        const val WALL = 0x0004
        const val REI_PUNCH = 0x0008
        const val ENEMY_PUNCH = 0x000F
        const val CAMERA = 0x0010
        const val TRIGGER = 0x0020
    }
}