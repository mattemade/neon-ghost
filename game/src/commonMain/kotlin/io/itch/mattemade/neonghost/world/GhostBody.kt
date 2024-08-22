package io.itch.mattemade.neonghost.world

import com.littlekt.math.PI_F
import com.soywiz.korma.geom.Angle
import io.itch.mattemade.neonghost.character.enemy.Enemy
import io.itch.mattemade.neonghost.scene.GhostOverlay
import io.itch.mattemade.utils.releasing.Releasing
import io.itch.mattemade.utils.releasing.Self
import org.jbox2d.collision.shapes.ChainShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import kotlin.math.cos
import kotlin.math.sin

class GhostBody(
    private val world: World,
    private val ghostOverlay: GhostOverlay,
) : Releasing by Self() {

    private val parts = 16
    val position: Vec2
        get() = body.position

    val targetEnemies: MutableSet<Enemy> = mutableSetOf()
    val body = world.createBody(
        BodyDef(
            type = BodyType.STATIC,
        ).apply {
            position.set(ghostOverlay.ghostPosition.x, ghostOverlay.ghostPosition.y)
        }).apply {
        createFixture(
            FixtureDef(
                shape = ChainShape().apply {
                    createLoop(
                        Array(parts) {
                            val angle = 2 * PI_F / parts * it
                            Vec2(
                                GhostOverlay.radiusX * cos(angle),
                                GhostOverlay.radiusY * sin(angle)
                            )
                        },
                        parts
                    )
                },
                friction = 0f,
                filter = Filter().apply {
                    categoryBits = ContactBits.GHOST_AOE
                    maskBits = ContactBits.ENEMY
                },
                userData = this@GhostBody,
                isSensor = true
            )
        )
    }.rememberTo { world.destroyBody(it) }

    fun updatePosition(vec2: Vec2) {
        body.setTransform(vec2, Angle.ZERO)
    }
}