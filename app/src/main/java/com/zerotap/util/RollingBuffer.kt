package com.zerotap.util

class RollingBuffer<T>(private val maxSize: Int) {
    private val buffer = ArrayDeque<T>(maxSize)

    @Synchronized
    fun add(item: T) {
        if (buffer.size >= maxSize) {
            buffer.removeFirst()
        }
        buffer.addLast(item)
    }

    @Synchronized
    fun getAll(): List<T> = buffer.toList()

    @Synchronized
    fun getSnapshot(): List<T> = buffer.toList()

    @Synchronized
    fun clear() {
        buffer.clear()
    }

    val size: Int
        @Synchronized get() = buffer.size
}
