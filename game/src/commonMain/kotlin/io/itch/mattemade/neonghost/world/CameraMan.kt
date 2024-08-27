package io.itch.mattemade.neonghost.world

import com.littlekt.Context
import com.littlekt.util.seconds
import io.itch.mattemade.neonghost.Game
import com.soywiz.korma.geom.Angle
import io.itch.mattemade.neonghost.pixelPerfectPosition
import io.itch.mattemade.neonghost.tempo.Choreographer
import org.jbox2d.collision.shapes.ChainShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import kotlin.time.Duration

class CameraMan(private val context: Context, private val choreographer: Choreographer, world: World, initialPosition: Vec2) {

    private var observing: ((Vec2) -> Unit)? = null

    val position: Vec2
        get() = body.position

    private val body = world.createBody(
        BodyDef(
            type = BodyType.STATIC,
        ).apply {
            position.set(initialPosition)
        })
    private val fixture = body.createFixture(
        FixtureDef(
            shape = ChainShape().apply {
                createLoop(
                    arrayOf(
                        Vec2(-Game.visibleWorldWidth / 2f, -Game.visibleWorldHeight / 2f),
                        Vec2(Game.visibleWorldWidth / 2f, -Game.visibleWorldHeight / 2f),
                        Vec2(Game.visibleWorldWidth / 2f, Game.visibleWorldHeight / 2f),
                        Vec2(-Game.visibleWorldWidth / 2f, Game.visibleWorldHeight / 2f)
                    ),
                    4
                )
            },
            friction = 0f,
            filter = Filter().apply {
                categoryBits = 0
            },
        )
    ) ?: error("cameraman fixture is null!")


    private val tempVec2 = Vec2()
    private val startPosition = Vec2()
    private var time = 0f
    private var startedLooking = 0f
    private var shouldBeAtSubjectAt = 0f
    fun update(dt: Duration) {
        time += dt.seconds
        observing?.let {
            it.invoke(tempVec2)
            if (time < shouldBeAtSubjectAt) {
                val delay = shouldBeAtSubjectAt - startedLooking
                val passed = time - startedLooking
                tempVec2.set(
                    startPosition.x + (tempVec2.x - startPosition.x) * interpolate(passed / delay),
                    startPosition.y + (tempVec2.y - startPosition.y) * interpolate(passed / delay)
                )
            }
            tempVec2.set(tempVec2.x.pixelPerfectPosition, tempVec2.y.pixelPerfectPosition)
            body.setTransform(tempVec2, Angle.ZERO)
        }
        choreographer.updatePosition(body.position.x, body.position.y)
        context.audio.setListenerPosition(body.position.x, body.position.y, -5f)
    }
    private fun interpolate(value: Float): Float = 3 * value * value - 2 * value * value * value

    fun lookAt(withinSeconds: Float = 0f, something: ((Vec2) -> Unit)?) {
        if (something == null) {
            shouldBeAtSubjectAt = 0f
            restricting = true
        } else {
            startedLooking = time
            shouldBeAtSubjectAt = time + withinSeconds
            startPosition.set(body.position)
            restricting = false
        }
        observing = something
    }

    private var restricting: Boolean = false
        get() = fixture.filterData.categoryBits != 0
        set(value) {
            if (field != value) {
                field = value
                fixture.filterData.categoryBits = if (value) ContactBits.CAMERA else 0
            }
        }
}