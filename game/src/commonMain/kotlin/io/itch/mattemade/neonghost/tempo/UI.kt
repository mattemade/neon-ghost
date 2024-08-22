package io.itch.mattemade.neonghost.tempo

import com.littlekt.Context
import com.littlekt.graph.node.resource.VAlign
import com.littlekt.graphics.Color
import com.littlekt.graphics.g2d.SpriteBatch
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputMapController
import com.littlekt.math.MutableVec2f
import com.littlekt.math.PI_F
import com.littlekt.math.Vec2f
import com.littlekt.math.geom.radians
import com.littlekt.util.Scaler
import com.littlekt.util.viewport.ScalingViewport
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.neonghost.character.enemy.Enemy
import io.itch.mattemade.neonghost.character.rei.Player
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.screenSpacePixelPerfect
import io.itch.mattemade.neonghost.world.Trigger
import io.itch.mattemade.utils.drawing.DelayedTextDrawer
import io.itch.mattemade.utils.drawing.MonoSpaceTextDrawer
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import kotlin.time.Duration

class UI(
    private val context: Context,
    private val assets: Assets,
    private val player: Player,
    private val controller: InputMapController<GameInput>,
    private val advanceDialogue: () -> Unit,
    private val activateInteraction: (Trigger) -> Unit,
    private val selectOption: (String) -> Unit,
) : Releasing by Self() {

    private val batch = SpriteBatch(context).releasing()
    private val shapeRenderer = ShapeRenderer(batch)
    private val viewport =
        ScalingViewport(scaler = Scaler.Fit(), Game.virtualWidth, Game.virtualHeight)
    private val camera = viewport.camera.apply {
        position.set(Game.virtualWidth / 2f, Game.virtualHeight / 2f, 0f)
    }
    val textDrawer = MonoSpaceTextDrawer(
        font = assets.texture.fontWhite,
        alphabet = ('A'..'Z').joinToString(separator = "") + ".,'0123456789:",
        fontLetterWidth = 5,
        fontLetterHeight = 9,
        fontHorizontalSpacing = 1,
        fontVerticalSpacing = 0,
        fontHorizontalPadding = 1,
    )
    private val delayedTextDrawer = DelayedTextDrawer(
        textDrawer,
        { 16f }
    )
    private val center = Vec2f(16f, 16f)
    private val tempVec2f = MutableVec2f(0f)
    private val tempVec2fb = MutableVec2f(0f)

    private val healthBarWidth = 64f
    private val healthBarHeight = 8f
    private val healthBarPadding = 2f
    private val maxHealthSlots = 5
    private val health = Array<Enemy?>(maxHealthSlots) { null }
    private val portraitPadding = 4f
    private val dialogPadding = 4f
    private val dialogHeight = 48f + portraitPadding * 2f
    private val dialogArrow = assets.texture.dialogueArrow
    private val interactionTitleWidth = 120f
    private val interactionTitleHeight = 28f
    private val optionsHeight = 28f

    private var activePortrait: String? = null
    private var isPortraitLeft: Boolean = false
    private var activeLines: List<String>? = null

    private var availableInteractionName: List<String>? = null
    var availableInteraction: Trigger? = null
        set(value) {
            field = value
            availableInteractionName = value?.properties?.get("title")?.string?.uppercase()?.let { listOf(it) }
        }
    var activeOptions: List<Pair<List<String>, String>>? = null
    var readyToSelectOption = false
    var activeOption = 0

    fun render(toMeasure: Float, movingToBeat: Boolean, movingOffbeat: Boolean) {
        viewport.apply(context)
        batch.begin(camera.viewProjection)
        shapeRenderer.filledCircle(center, 15f, color = Color.WHITE.toFloatBits())
        shapeRenderer.filledCircle(
            center,
            13f,
            color = (if (movingToBeat) Color.DARK_GREEN else if (movingOffbeat) Color.DARK_RED else Color.DARK_YELLOW).toFloatBits()
        )
        for (i in 0 until 4) {
            tempVec2f.set(15f, 0f).rotate((PI_F * i / 2f).radians).add(center)
            tempVec2fb.set(10f, 0f).rotate((PI_F * i / 2f).radians).add(center)
            shapeRenderer.line(tempVec2fb, tempVec2f, color = Color.WHITE, thickness = 1f)
        }
        tempVec2f.set(14f, 0f).rotate((toMeasure * PI_F * 2 - PI_F / 2f).radians).add(center)
        shapeRenderer.line(center, tempVec2f, color = Color.WHITE, thickness = 1f)

        renderProgressbar(
            34f,
            healthBarPadding,
            healthBarWidth,
            healthBarHeight,
            player.health.toFloat() / player.initialHealth,
            Color.GRAY,
            Color.GREEN
        )

        for (i in 0 until maxHealthSlots) {
            val enemy = health[i]
            if (enemy != null) {
                val isLeft = i % 2 == 1
                val row = (i + 1) / 2
                val startX = if (isLeft) 34f else Game.virtualWidth - healthBarWidth - 2f
                val startY = (row + 1) * healthBarPadding + row * healthBarHeight
                renderProgressbar(
                    startX,
                    startY,
                    healthBarWidth,
                    healthBarHeight,
                    enemy.health.toFloat() / enemy.initialHeath,
                    Color.GRAY,
                    Color.RED
                )
            }
        }

        availableInteractionName?.let {
            shapeRenderer.filledRectangle(
                (Game.virtualWidth - interactionTitleWidth) / 2f,
                dialogPadding,
                interactionTitleWidth,
                interactionTitleHeight,
                color = Color.BLACK.toFloatBits()
            )
            textDrawer.drawText(
                batch,
                it,
                (Game.virtualWidth / 2f).screenSpacePixelPerfect,
                (dialogPadding + interactionTitleHeight / 2f).screenSpacePixelPerfect + 0.5f,
            )
        }

        activeLines?.let { lines ->
            shapeRenderer.filledRectangle(
                0f,//dialogPadding,
                0f,//dialogPadding,
                Game.virtualWidth.toFloat(),// - dialogPadding * 2f,
                dialogHeight + dialogPadding*2f + if (activeOptions != null) optionsHeight else 0f,//dialogHeight,
                color = Color.BLACK.toFloatBits()
            )

            var paddingLeft = 0f
            var paddingRight = 0f
            activePortrait?.let { portraitName ->
                assets.texture.portraits[portraitName]?.let { portrait ->
                    if (isPortraitLeft) {
                        paddingLeft = portrait.width + portraitPadding * 2f
                    } else {
                        paddingRight = portrait.width + portraitPadding * 2f
                    }
                    batch.draw(
                        portrait,
                        if (isPortraitLeft) dialogPadding + portraitPadding else Game.virtualWidth - portrait.width - portraitPadding - dialogPadding,
                        dialogPadding + (dialogHeight - portrait.height) / 2f,
                        width = portrait.width.toFloat(),
                        height = portrait.height.toFloat(),
                        flipX = !isPortraitLeft
                    )
                }
            }

            textDrawer.drawText(
                batch,
                lines,
                (Game.virtualWidth / 2f).screenSpacePixelPerfect,
                (dialogPadding + portraitPadding).screenSpacePixelPerfect,
                vAlign = VAlign.TOP
            )

            activeOptions?.let { options ->
                val count = options.size
                val widthPerOption = (Game.virtualWidth - portraitPadding * 2f) / count
                val y = (dialogHeight + dialogPadding * 2f).screenSpacePixelPerfect
                options.forEachIndexed { index, pair ->
                    val text = pair.first
                    val x = (portraitPadding + widthPerOption/2f + index * widthPerOption).screenSpacePixelPerfect
                    textDrawer.drawText(
                        batch,
                        text,
                        x,
                        y,
                        vAlign = VAlign.TOP
                    )

                    if (index == activeOption) {
                        batch.draw(
                            dialogArrow,
                            x - dialogArrow.width / 2f,
                            dialogPadding + dialogHeight - portraitPadding - dialogArrow.height,
                            width = dialogArrow.width.toFloat(),
                            height = dialogArrow.height.toFloat()
                        )
                    }
                }

            } ?: run {
                batch.draw(
                    dialogArrow,
                    Game.virtualWidth / 2f - dialogArrow.width / 2f,
                    dialogPadding + dialogHeight - portraitPadding - dialogArrow.height,
                    width = dialogArrow.width.toFloat(),
                    height = dialogArrow.height.toFloat()
                )
            }
        }

        batch.end()
    }

    private fun renderProgressbar(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        progress: Float,
        bgColor: Color,
        color: Color
    ) {
        shapeRenderer.filledRectangle(
            x,
            y,
            width,
            height,
            color = bgColor.toFloatBits()
        )
        shapeRenderer.filledRectangle(
            x,
            y,
            width * progress,
            healthBarHeight,
            color = color.toFloatBits()
        )
    }

    fun showHealth(enemy: Enemy) {
        for (i in 0 until maxHealthSlots) {
            if (health[i] == null) {
                health[i] = enemy
                return
            }
        }
    }

    fun stopShowingHealth(deadEnemies: MutableList<Enemy>) {
        for (i in 0 until maxHealthSlots) {
            if (deadEnemies.contains(health[i])) {
                health[i] = null
            }
        }
    }

    fun showDialogLine(portrait: String, isLeft: Boolean, text: String) {
        activePortrait = portrait
        isPortraitLeft = isLeft
        activeLines = text.uppercase().split("\\")

    }

    fun stopDialog() {
        activePortrait = null
        activeLines = null
    }

    fun update(dt: Duration) {
        activeLines?.let {
            activeOptions?.let { options ->
                val xMovement = controller.axis(GameInput.HORIZONTAL)
                if (readyToSelectOption && xMovement != 0f) {
                    readyToSelectOption = false
                    activeOption += if (xMovement < 0f) -1 else 1
                    if (activeOption < 0) {
                        activeOption = options.size - 1
                    } else if (activeOption >= options.size) {
                        activeOption = 0
                    }
                }
                if (xMovement == 0f) {
                    readyToSelectOption = true
                }
                if (controller.pressed(GameInput.ANY_ACTION)) {
                    selectOption(options[activeOption].second)
                    return
                }
            }
            if (controller.pressed(GameInput.ANY_ACTION)) {
                advanceDialogue()
                return
            }
        }
        availableInteraction?.let {
            if (controller.pressed(GameInput.ANY_ACTION)) {
                activateInteraction(it)
                return
            }
        }

    }

    fun hideOptions() {
        activeOptions = null
    }

    fun showOptions(options: List<Pair<String, String>>) {
        activeOptions = options.map { listOf(it.first.uppercase()) to it.second }
        activeOption = 0
        readyToSelectOption = true
    }
}