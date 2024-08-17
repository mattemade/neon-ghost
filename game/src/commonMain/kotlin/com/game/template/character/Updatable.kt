package com.game.template.character

import kotlin.time.Duration

interface Updatable {
    fun update(dt: Duration, millis: Float, toBeat: Float)
}