package io.itch.mattemade.neonghost.scene

class TimedAction(
    private val timeToAct: Float,
    private val executing: (Float) -> Unit,
    private val finished: () -> Unit
) {
    private var time = timeToAct

    fun update(seconds: Float): Float {
        time -= seconds
        if (time <= 0f) {
            finished()
        } else {
            executing(time / timeToAct)
        }
        return time
    }
}