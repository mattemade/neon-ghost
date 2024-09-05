package io.itch.mattemade.neonghost.character.enemy

import com.littlekt.math.MutableVec2f
import io.itch.mattemade.neonghost.character.rei.Player

class EnemyAi(
    private val player: Player,
    private val enemies: List<Enemy>,

    ) {

    private val knockback = 2.5f
    private val spnk = 2f
    private val pnk = 5f
    private val leftClose = TargetPoint(player, -Enemy.defaultPunchDistance)
    private val rightClose = TargetPoint(player, Enemy.defaultPunchDistance)
    private val leftFar = TargetPoint(player, -knockback * Enemy.defaultPunchDistance)
    private val rightFar = TargetPoint(player, knockback * Enemy.defaultPunchDistance)

    private val closePoints = listOf(leftClose, rightClose)
    private val farPoints = listOf(leftFar, rightFar)
    private val points = closePoints + farPoints

    private val surroundingPoints = listOf(
        TargetPoint(player, -knockback * Enemy.defaultPunchDistance, -knockback * Enemy.defaultPunchDistance),
        TargetPoint(player, knockback * Enemy.defaultPunchDistance, -knockback * Enemy.defaultPunchDistance),
        TargetPoint(player, -knockback * Enemy.defaultPunchDistance, knockback * Enemy.defaultPunchDistance),
        TargetPoint(player, knockback * Enemy.defaultPunchDistance, knockback * Enemy.defaultPunchDistance),
        TargetPoint(player, 0f, -pnk * Enemy.defaultPunchDistance),
        TargetPoint(player, 0f, pnk * Enemy.defaultPunchDistance),
        TargetPoint(player, -spnk * Enemy.defaultPunchDistance, pnk * Enemy.defaultPunchDistance),
        TargetPoint(player, -spnk * Enemy.defaultPunchDistance, -pnk * Enemy.defaultPunchDistance),
        TargetPoint(player, spnk * Enemy.defaultPunchDistance, pnk * Enemy.defaultPunchDistance),
        TargetPoint(player, spnk * Enemy.defaultPunchDistance, -pnk * Enemy.defaultPunchDistance),
    )

    fun reset() {
        points.forEach {
            it.occupiedBy?.target = null
        }
        surroundingPoints.forEach {
            it.occupiedBy?.target = null
        }
    }

    private val tempVecknockback = MutableVec2f()
    fun update() {
        //enemies.forEach { it.target = null }
        if (player.hitCooldown > 0f) {
            return
        }

        val activeEnemies = enemies.filter { it.health > 0 && it.hitCooldown <= 0f }
        val activeFreeEnemies = activeEnemies.filter { it.target == null }
        if (activeEnemies.isEmpty()) {
            return
        }

        points.forEach {
            //it.occupiedBy = null
            it.updatePosition()
        }
        surroundingPoints.forEach {
            it.updatePosition()
        }

        if (activeEnemies.size == 1) {
            val enemy = activeEnemies.first()
            val closestPoint =
                closePoints.minBy { tempVecknockback.set(enemy.x, enemy.y).distance(it.position) }
            enemy.target = closestPoint
            return
        }

        points.forEach { point ->
            point.sortedEnemies.clear()
            point.sortedEnemies.addAll(activeFreeEnemies.map {
                it to tempVecknockback.set(it.x, it.y).distance(point.position)
            })
            point.sortedEnemies.sortBy { it.second }
        }

        if (activeFreeEnemies.isNotEmpty()) {
            val closestClosePoint = points.minBy { it.sortedEnemies.first().second }
            closestClosePoint.occupyIfFree()
            val oppositeFarToClosestClosePoint =
                if (closestClosePoint == leftClose) rightFar else leftFar
            oppositeFarToClosestClosePoint.occupyIfFree()
            val oppositeClosePoint = if (closestClosePoint == leftClose) rightClose else leftClose
            oppositeClosePoint.occupyIfFree()
            val oppositeFarToOppositeClosePoint =
                if (oppositeClosePoint == leftClose) rightFar else leftFar
            oppositeFarToOppositeClosePoint.occupyIfFree()
            println("${leftFar.occupiedBy} ${leftClose.occupiedBy} ${rightClose} ${rightFar.occupiedBy}")

            if (closestClosePoint.sortedEnemies.isNotEmpty()) {
                surroundingPoints.forEach {
                    it.updatePosition()
                }
                closestClosePoint.sortedEnemies.forEachIndexed { index, enemy ->
                    enemy.first.target = surroundingPoints[index]
                }
            }
        }

        if (leftClose.occupiedBy == null && rightFar.occupiedBy != null) { // x 0 p x 1
            // should occupy left point
            if (leftFar.occupiedBy != null) { // 1 0 p x 1
                shuffleFarToClose(leftFar, leftClose, rightClose, rightFar)
            } else { // 0 0 p x 1
                shuffleOthersToClose(leftClose, leftFar, rightClose, rightFar)
            }
        }
        if (rightClose.occupiedBy == null && leftFar.occupiedBy != null) { // 1 x p 0 x
            // should occupy left point
            if (rightFar.occupiedBy != null) { // 1 x p 0 1
                shuffleFarToClose(rightFar, rightClose, leftClose, leftFar)
            } else { // 1 x p 0 0
                shuffleOthersToClose(rightClose, rightFar, leftClose, leftFar)
            }
        }
    }

    private fun shuffleFarToClose(far: TargetPoint, close: TargetPoint, otherClose: TargetPoint, otherFar: TargetPoint) { // 1 0 p x 1
        otherClose.occupiedBy?.let { // 1 0 p 1 1
            far.occupiedBy?.let {
                it.target = close // 0 1 p 1 1
                addFromSurrounding(far) // 1 1 p 1 1
            }
        } ?: run { // 1 0 p 0 1
            far.occupiedBy?.let {
                it.target = close // 0 1 p 0 1
                addFromSurrounding(otherClose)
                addFromSurrounding(otherFar)
            }
        }
    }

    private fun shuffleOthersToClose( // 0 0 p x 1
        close: TargetPoint,
        far: TargetPoint,
        otherClose: TargetPoint,
        otherFar: TargetPoint
    ) {
        otherClose.occupiedBy?.let { // 0 0 p 1 1
            otherFar.occupiedBy?.let { //  0 0 p 1 1
                it.target = far  //  1 0 p 1 0
                addFromSurrounding(close)
                addFromSurrounding(otherFar)
            } ?: run {
                // should not be possible
            }
        } ?: otherFar.occupiedBy?.let { // 0 0 p 0 1
            it.target = otherClose // 0 0 p 1 0
            addFromSurrounding(far)
            addFromSurrounding(close)
            addFromSurrounding(otherFar)
        }
    }

    private fun addFromSurrounding(to: TargetPoint) {
        for (i in surroundingPoints.size - 1 downTo 0) {
            val point = surroundingPoints[i]
            point.occupiedBy?.let {
                it.target = to
                return
            }
        }
    }

    private fun TargetPoint?.occupyIfFree() {
        if (this == null) {
            return
        }
        if (this.occupiedBy != null) {
            return
        }
        val enemy = sortedEnemies.firstOrNull()?.first ?: return
        enemy.target = this
        points.forEach {
            it.sortedEnemies.removeAll { it.first === enemy }
        }
    }

    class TargetPoint(private val player: Player, x: Float, y: Float = 0f) {
        val relativePosition = MutableVec2f(x, y)
        val sortedEnemies = mutableListOf<Pair<Enemy, Float>>()
        var occupiedBy: Enemy? = null

        val position = MutableVec2f(relativePosition)

        fun updatePosition() {
            position.set(player.x, player.y).add(relativePosition)
        }


    }
}