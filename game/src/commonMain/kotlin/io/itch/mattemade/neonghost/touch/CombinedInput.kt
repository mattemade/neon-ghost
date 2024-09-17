package io.itch.mattemade.neonghost.touch

import com.littlekt.input.InputMapController
import io.itch.mattemade.blackcat.input.GameInput

class CombinedInput(
    private val input: InputMapController<GameInput>,
    private val virtualController: VirtualController
) {
    fun axis(axis: GameInput): Float {
        return input.axis(axis) + virtualController.axis(axis)
    }

    fun pressed(action: GameInput): Boolean {
        val keyResult = input.pressed(action)
        if (virtualController.isVisible && keyResult) {
            virtualController.isVisible = false
        }
        return keyResult || virtualController.pressed(action)
    }

    fun down(action: GameInput): Boolean {
        return input.down(action) || virtualController.down(action)
    }


}