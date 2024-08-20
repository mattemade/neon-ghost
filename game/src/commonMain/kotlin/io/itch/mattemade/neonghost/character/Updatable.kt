package io.itch.mattemade.neonghost.character

import kotlin.time.Duration

interface Updatable {
    fun update(dt: Duration, millis: Float, toBeat: Float, toMeasure: Float)
}