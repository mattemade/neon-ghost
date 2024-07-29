package io.itch.mattemade.utils.releasing

import com.littlekt.Releasable

class MutableDisposingList<T : Releasable>(
    private val list: MutableList<T> = mutableListOf(),
    private val onDisposed: (T) -> Unit
) : MutableList<T> by list,
    Releasable {

    override fun add(element: T): Boolean {
        return list.add(element)
    }

    override fun clear() {
        list.forEach { it.also(onDisposed).release() }
        list.clear()
    }

    override fun removeAt(index: Int): T {
        return list.removeAt(index).also { it.also(onDisposed).release() }
    }

    override fun set(index: Int, element: T): T =
        list.set(index, element).also { it.also(onDisposed).release() }

    override fun retainAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun iterator(): MutableIterator<T> = IteratorWrapper(list.listIterator()) {
        it.also(onDisposed).release()
    }

    override fun listIterator(): MutableListIterator<T> = IteratorWrapper(list.listIterator()) {
        TODO("Not yet implemented")
    }

    override fun remove(element: T): Boolean {
        element.also(onDisposed).release()
        return list.remove(element)
    }

    override fun release() {
        clear()
    }

    private class IteratorWrapper<T : Releasable>(
        private val listIterator: MutableListIterator<T>,
        private val onRemoved: (T) -> Unit
    ) : MutableListIterator<T> by listIterator {

        private lateinit var current: T
        override fun next(): T =
            listIterator.next().also { current = it }

        override fun remove() {
            onRemoved(current)
            listIterator.remove()
        }

        override fun set(element: T) {
            onRemoved(current)
            listIterator.set(element)
        }
    }
}

inline fun <T : Releasable> mutableDisposableListOf(noinline onDisposed: (T) -> Unit): MutableDisposingList<T> =
    MutableDisposingList(onDisposed = onDisposed)
