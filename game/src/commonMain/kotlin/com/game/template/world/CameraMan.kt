package com.game.template.world

import com.game.template.Game
import com.soywiz.korma.geom.Angle
import org.jbox2d.collision.shapes.ChainShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World

class CameraMan(world: World) {

    private var observing: (() -> Vec2)? = null
    var smooth: Boolean = false

    val position: Vec2
        get() = body.position

    private val body = world.createBody(
        BodyDef(
            type = BodyType.STATIC,
        ).apply {
            position.set(Game.visibleWorldWidth / 2f, Game.visibleWorldHeight / 2f)
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
    fun update() {
        observing?.invoke()?.let {
            body.setTransform(it, Angle.ZERO)
            if (smooth) {
                val distance = tempVec2.set(it).subLocal(body.position).length()

            } else {
            }
        }
    }

    fun lookAt(something: (() -> Vec2)?) {
        observing = something
    }

    var restricting: Boolean = false
        get() = fixture.filterData.categoryBits != 0
        set(value) {
            if (field != value) {
                field = value
                fixture.filterData.categoryBits = if (value) ContactBits.CAMERA else 0
            }
        }
}