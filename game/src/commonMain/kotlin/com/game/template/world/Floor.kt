package com.game.template.world

import com.littlekt.math.Rect
import com.littlekt.math.Vec2f
import org.jbox2d.collision.shapes.ChainShape
import org.jbox2d.collision.shapes.PolygonShape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.BodyDef
import org.jbox2d.dynamics.BodyType
import org.jbox2d.dynamics.Filter
import org.jbox2d.dynamics.FixtureDef
import org.jbox2d.dynamics.World
import org.jbox2d.dynamics.contacts.Position

class Floor(world: World, val rect: Rect) {

    val renderPath = mutableListOf<Vec2f>()
    private val body = world.createBody(
        BodyDef(
        type = BodyType.STATIC,
    ).apply {
        position.set(rect.x, rect.y)
    }).apply {
        createFixture(
            FixtureDef(
            shape = ChainShape().apply {
                createLoop(
                    arrayOf(
                        Vec2(0f, 0f),
                        Vec2(rect.width, 0f),
                        Vec2(rect.width + rect.height, -rect.height),
                        Vec2(rect.height, -rect.height)
                    ).also {
                        renderPath.addAll(it.map { Vec2f(it.x, it.y) })
                    },
                    4
                )
            },
            friction = 0f,
            //filter = Filter().apply(filter),
            userData = userData
        )
        )
    }
}