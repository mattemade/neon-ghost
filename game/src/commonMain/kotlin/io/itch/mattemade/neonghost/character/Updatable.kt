package io.itch.mattemade.neonghost.character

import kotlin.time.Duration

interface Updatable {
    fun update(dt: Duration, millis: Float, notAdjustedDt: Duration, toBeat: Float, toMeasure: Float, isFighting: Boolean)
}