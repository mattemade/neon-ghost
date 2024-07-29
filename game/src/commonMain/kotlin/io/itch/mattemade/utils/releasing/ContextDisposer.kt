package io.itch.mattemade.utils.releasing

fun interface ContextReleaser {
    fun release(context: Any?)
}
