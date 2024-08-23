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

    MAGIC,
    ATTACK,

    START,
    SELECT,
    ANY,
    ANY_ACTION,
}

fun Context.bindInputs(): InputMapController<GameInput> =
    InputMapController<GameInput>(input).apply {
        // the 'A' and 'left arrow' keys and the 'x-axis of the left stick' with trigger the 'MOVE_LEFT' input type
        val anyKey = mutableListOf<Key>()
        val anyActionKey = mutableListOf<Key>()
        val anyButton = mutableListOf<GameButton>()
        val anyActionButton = mutableListOf<GameButton>()
        fun List<Key>.any(): List<Key> = this.also { anyKey.addAll(this) }
        fun List<Key>.action(): List<Key> = this.also { anyActionKey.addAll(this) }
        fun List<GameButton>.any(): List<GameButton> = this.also { anyButton.addAll(this) }
        fun List<GameButton>.action(): List<GameButton> = this.also { anyActionButton.addAll(this) }


        addBinding(
            GameInput.RIGHT,
            listOf(Key.D, Key.ARROW_RIGHT).any(),
            axes = listOf(GameAxis.LX),
            buttons = listOf(GameButton.RIGHT).any()
        )
        addBinding(
            GameInput.LEFT,
            listOf(Key.A, Key.ARROW_LEFT).any(),
            axes = listOf(GameAxis.LX),
            buttons = listOf(GameButton.LEFT).any()
        )
        addAxis(GameInput.HORIZONTAL, GameInput.RIGHT, GameInput.LEFT)

        // creates an axis based off the DOWN and UP input types
        addBinding(
            GameInput.UP,
            listOf(Key.W, Key.ARROW_UP).any(),
            axes = listOf(GameAxis.LY),
            buttons = listOf(GameButton.UP).any()
        )
        addBinding(
            GameInput.DOWN,
            listOf(Key.S, Key.ARROW_DOWN).any(),
            axes = listOf(GameAxis.LY),
            buttons = listOf(GameButton.DOWN).any()
        )
        addAxis(GameInput.VERTICAL, GameInput.DOWN, GameInput.UP)

        addBinding(
            GameInput.MAGIC,
            listOf(Key.J, Key.X).any().action(),
            buttons = listOf(GameButton.XBOX_A, GameButton.XBOX_Y).any().action()
        )
        addBinding(
            GameInput.ATTACK,
            listOf(Key.K, Key.Z).any().action(),
            buttons = listOf(GameButton.XBOX_X, GameButton.XBOX_B).any().action()
        )

        addBinding(
            GameInput.START,
            listOf(Key.ENTER).any(),
            buttons = listOf(GameButton.START).any()
        )
        addBinding(GameInput.SELECT, listOf(Key.R), buttons = listOf(GameButton.SELECT))

        addBinding(GameInput.ANY, anyKey, buttons = anyButton)
        addBinding(GameInput.ANY_ACTION, anyActionKey, buttons = anyActionButton)

        mode = InputMapController.InputMode.GAMEPAD

        input.addInputProcessor(this)
    }