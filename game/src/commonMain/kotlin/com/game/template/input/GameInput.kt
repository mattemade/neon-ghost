package io.itch.mattemade.blackcat.input

import com.littlekt.Context
import com.littlekt.input.GameAxis
import com.littlekt.input.GameButton
import com.littlekt.input.InputMapController
import com.littlekt.input.Key

enum class GameInput {
    LEFT,
    RIGHT,
    HORIZONTAL, // mapped from LEFT and RIGHT based on axis

    UP,
    DOWN,
    VERTICAL, // mapped from UP and DOWN based on axis, for climbing

    JUMP,
    ATTACK,

    PAUSE,
    RESTART,
    ANY,
}

fun Context.bindInputs(): InputMapController<GameInput> =
    InputMapController<GameInput>(input).apply {
        // the 'A' and 'left arrow' keys and the 'x-axis of the left stick' with trigger the 'MOVE_LEFT' input type
        val anyKey = mutableListOf<Key>()
        val anyButton = mutableListOf<GameButton>()
        fun List<Key>.any(): List<Key> = this.also { anyKey.addAll(this) }
        fun List<GameButton>.any(): List<GameButton> = this.also { anyButton.addAll(this) }


        addBinding(GameInput.RIGHT, listOf(Key.D, Key.ARROW_RIGHT).any(), axes = listOf(GameAxis.LX), buttons = listOf(GameButton.RIGHT).any())
        addBinding(GameInput.LEFT, listOf(Key.A, Key.ARROW_LEFT).any(), axes = listOf(GameAxis.LX), buttons = listOf(GameButton.LEFT).any())
        addAxis(GameInput.HORIZONTAL, GameInput.RIGHT, GameInput.LEFT)

// creates an axis based off the DOWN and UP input types
        addBinding(GameInput.UP, listOf(Key.W, Key.ARROW_UP).any(), axes = listOf(GameAxis.LY), buttons = listOf(GameButton.UP).any())
        addBinding(GameInput.DOWN, listOf(Key.S, Key.ARROW_DOWN).any(), axes = listOf(GameAxis.LY), buttons = listOf(GameButton.DOWN).any())
        addAxis(GameInput.VERTICAL, GameInput.DOWN, GameInput.UP)

        addBinding(GameInput.JUMP, listOf(Key.SPACE, Key.K, Key.Z).any(), buttons = listOf(GameButton.XBOX_A).any())
        addBinding(GameInput.ATTACK, listOf(Key.SHIFT_LEFT, Key.J, Key.X).any(), buttons = listOf(GameButton.XBOX_X))

        addBinding(GameInput.PAUSE, listOf(Key.P).any(), buttons = listOf(GameButton.START).any())
        addBinding(GameInput.RESTART, listOf(Key.R), buttons = listOf(GameButton.SELECT))

        addBinding(GameInput.ANY, anyKey, buttons = anyButton)

        mode = InputMapController.InputMode.GAMEPAD

        input.addInputProcessor(this)
    }