package com.sameerasw.essentials.island.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

// Lets settings UI ask a running island to briefly show a sample notification queue.
object IslandPreviewRequests {
    private val _queue = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val queue: SharedFlow<Unit> = _queue

    fun showQueue() {
        _queue.tryEmit(Unit)
    }
}
