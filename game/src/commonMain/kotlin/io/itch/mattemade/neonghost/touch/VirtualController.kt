package io.itch.mattemade.neonghost.touch

import com.littlekt.Context
import com.littlekt.graphics.Camera
import com.littlekt.graphics.Color
import com.littlekt.graphics.MutableColor
import com.littlekt.graphics.g2d.Batch
import com.littlekt.graphics.g2d.shape.ShapeRenderer
import com.littlekt.graphics.toFloatBits
import com.littlekt.input.InputProcessor
import com.littlekt.input.Pointer
import com.littlekt.math.MutableVec2f
import com.littlekt.math.Vec2f
import io.itch.mattemade.blackcat.input.GameInput
import io.itch.mattemade.neonghost.Assets
import io.itch.mattemade.neonghost.Game
import io.itch.mattemade.utils.render.DirectRender
import org.jbox2d.dynamics.contacts.Position
import kotlin.time.Duration

class VirtualController(val context: Context, val assets: Assets, var zoom: Float) {

    var isMagic = false
    var isVisible = false

    private var wasTouching = false
    private val directRender =
        DirectRender(context, Game.virtualWidth, Game.virtualHeight, ::update, ::render)
    private var shapeRenderer: ShapeRenderer? = null
    private var screenPadding = 0f
    private var cameraX = 0f
    private var cameraY = 0f

    private var stickCenterX = 0f
    private var stickCenterY = 0f
    private val stickColor = MutableColor(0.8f, 0.8f, 0.8f, 0.3f).toFloatBits()
    private val stickDownColor = MutableColor(0.8f, 0.8f, 0.8f, 0.8f).toFloatBits()
    private var punchButtonCenterX = 0f
    private var punchButtonCenterY = 0f
    private val punchButtonColor = MutableColor(0.8f, 0.8f, 0.8f, 0.3f).toFloatBits()
    private val punchButtonDownColor = MutableColor(0.8f, 0.8f, 0.8f, 0.8f).toFloatBits()
    private var magicButtonCenterX = 0f
    private var magicButtonCenterY = 0f
    private val magicButtonColor = MutableColor(0.765f, 0.545f, 0.816f, 0.3f).toFloatBits()
    private val magicButtonDownColor = MutableColor(0.765f, 0.545f, 0.816f, 0.9f).toFloatBits()
    //private val magicButtonDownColor = MutableColor(0.416f, 0.22f, 0.522f, 0.9f).toFloatBits()

    private var stickX = 0f
    private var stickY = 0f
    private var stickRadius = 0f
    private var stickDownRadius = 0f
    private var stickExtraRadius = 0f

    private var buttonRadius = 0f

    private var stickDown = -1
    private var punchPressed = false
    private var punchDown = false
    private var magicPressed = false
    private var magicDown = false

    private val pointerPositions = mutableMapOf<Int, MutableVec2f>()

    init {
        context.input.addInputProcessor(object : InputProcessor {
            override fun touchDown(screenX: Float, screenY: Float, pointer: Pointer): Boolean {
                if (!isVisible) {
                    return false
                }
                pointerPositions.getOrPut(pointer.index) { MutableVec2f(0f, 0f)}.set(screenX, screenY).divAssign(zoom)
                return true
            }

            override fun touchDragged(screenX: Float, screenY: Float, pointer: Pointer): Boolean {
                if (!isVisible) {
                    return false
                }
                pointerPositions[pointer.index]?.set(screenX, screenY)?.divAssign(zoom)
                return true
            }

            override fun touchUp(screenX: Float, screenY: Float, pointer: Pointer): Boolean {
                if (!isVisible) {
                    return false
                }
                pointerPositions[pointer.index]?.set(-100f, -100f)
                return true
            }
        })
    }

    val tempVec2f = MutableVec2f()
    private fun doesSomethingPress(x: Float, y: Float, radius: Float): Int {
        pointerPositions.forEach {
            val position = it.value
            if (position.x >= 0f && position.y >= 0f && tempVec2f.set(position).subtract(x, y).length() <= radius) {
                return it.key
            }
        }
        return -1
    }

    private fun update(dt: Duration, camera: Camera) {
        camera.position.set(cameraX, cameraY, 0f)
    }

    private fun render(dt: Duration, batch: Batch) {
        if (!isVisible || !assets.isLoaded) {
            return
        }
        if (shapeRenderer == null) {
            shapeRenderer = ShapeRenderer(batch, assets.texture.white)
        }
        shapeRenderer?.apply {
            filledCircle(
                stickCenterX,
                stickCenterY,
                stickRadius,
                color = stickColor
            )
            if (stickDown >= 0) {
                filledCircle(
                    stickCenterX + stickX * stickRadius,
                    stickCenterY + stickY * stickRadius,
                    stickDownRadius,
                    color = stickDownColor
                )
            }
            filledCircle(
                punchButtonCenterX,
                punchButtonCenterY,
                buttonRadius,
                color = if (punchDown) punchButtonDownColor else punchButtonColor
            )
            if (isMagic) {
                filledCircle(
                    magicButtonCenterX,
                    magicButtonCenterY,
                    buttonRadius,
                    color = if (magicDown) magicButtonDownColor else magicButtonColor
                )
            }
        }
    }

    fun update() {
        if (context.input.isTouching) {
            wasTouching = true
            if (punchDown) {
                punchPressed = false
                punchDown = doesSomethingPress(punchButtonCenterX, punchButtonCenterY, buttonRadius) >= 0
            } else {
                punchPressed = doesSomethingPress(punchButtonCenterX, punchButtonCenterY, buttonRadius) >= 0
                punchDown = punchPressed
            }
            if (isMagic) {
                if (magicDown) {
                    magicPressed = false
                    magicDown =
                        doesSomethingPress(magicButtonCenterX, magicButtonCenterY, buttonRadius) >= 0
                } else {
                    magicPressed =
                        doesSomethingPress(magicButtonCenterX, magicButtonCenterY, buttonRadius) >= 0
                    magicDown = magicPressed
                }
            }
            if (stickDown == -1) {
                stickDown = doesSomethingPress(stickCenterX, stickCenterY, stickExtraRadius)
            }
            if (stickDown >= 0) {
                pointerPositions[stickDown]?.let {
                    if (it.x < 0f) {
                        stickDown = -1
                        stickX = 0f
                        stickY = 0f
                    } else {
                        tempVec2f.set(it).subtract(stickCenterX, stickCenterY)
                        tempVec2f.divAssign(stickRadius)
                        val resultLength = tempVec2f.length()
                        if (resultLength >= 1f) {
                            tempVec2f.divAssign(resultLength)
                        }
                        stickX = tempVec2f.x
                        stickY = tempVec2f.y
                    }
                } ?: run {
                    stickDown = -1
                    stickX = 0f
                    stickY = 0f
                }
            } else {
                stickX = 0f
                stickY = 0f
            }
        } else if (wasTouching) {
            wasTouching = false
            stickDown = -1
            stickX = 0f
            stickY = 0f
            punchPressed = false
            punchDown = false
            magicPressed = false
            magicDown = false
        }
    }

    fun resize(width: Int, height: Int) {
        directRender.resize(width, height)
        cameraX = width / 2f
        cameraY = height / 2f

        screenPadding = height / 16f
        stickRadius = (height - screenPadding * 2f) / 6f
        stickDownRadius = stickRadius * 0.75f
        stickExtraRadius = stickRadius * 1.5f
        buttonRadius = (height - screenPadding * 2f) / 9f

        stickCenterX = screenPadding*3f + stickRadius
        stickCenterY = directRender.postViewport.virtualHeight - (screenPadding*2f + stickRadius)

        punchButtonCenterX = directRender.postViewport.virtualWidth - (screenPadding*2f + stickRadius)
        punchButtonCenterY = directRender.postViewport.virtualHeight - (screenPadding*2f + stickRadius)

        magicButtonCenterX = directRender.postViewport.virtualWidth - (screenPadding*2f + stickRadius)
        magicButtonCenterY = directRender.postViewport.virtualHeight - (screenPadding*2f + stickRadius * 2f)
    }

    fun render(dt: Duration) {
        directRender.render(dt)
    }


    fun pressed(action: GameInput): Boolean =
        when (action) {
            GameInput.ATTACK -> punchPressed
            GameInput.MAGIC -> magicPressed
            GameInput.ANY_ACTION -> punchPressed || magicPressed
            else -> false
        }

    fun down(action: GameInput): Boolean =
        when (action) {
            GameInput.ATTACK -> punchDown
            GameInput.MAGIC -> magicDown
            GameInput.ANY_ACTION -> punchDown || magicDown
            else -> false
        }

    fun axis(axis: GameInput): Float =
        when (axis) {
            GameInput.HORIZONTAL -> stickX
            GameInput.VERTICAL -> stickY
            else -> 0f
        }
}