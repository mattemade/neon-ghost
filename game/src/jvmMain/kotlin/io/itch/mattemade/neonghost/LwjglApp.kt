package io.itch.mattemade.neonghost

import com.littlekt.createLittleKtApp

fun main() {
    createLittleKtApp {
        width = 960
        height = 540
        title = "Neon Ghost"
    }.start {
        Game(it, onLowPerformance = {}, savedStateOverride = null)
    }
}