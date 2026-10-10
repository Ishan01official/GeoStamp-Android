package com.geostamp.camera.dual

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A short-lived lifecycle that follows [parent] until [destroy]. CameraX keys concurrent cameras by
 * lifecycle owner and reuses the first composition settings it saw for that owner; binding each dual
 * session to a fresh owner makes a moved or resized inset actually take effect.
 */
class BindingLifecycleOwner(private val parent: LifecycleOwner) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    private val observer = LifecycleEventObserver { _, event ->
        if (registry.currentState != Lifecycle.State.DESTROYED) registry.currentState = event.targetState.coerceAtMost(Lifecycle.State.RESUMED)
    }

    init {
        registry.currentState = parent.lifecycle.currentState.takeIf { it != Lifecycle.State.DESTROYED } ?: Lifecycle.State.INITIALIZED
        parent.lifecycle.addObserver(observer)
    }

    fun destroy() {
        parent.lifecycle.removeObserver(observer)
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.currentState = Lifecycle.State.DESTROYED
    }

    private fun Lifecycle.State.coerceAtMost(max: Lifecycle.State) = if (this > max) max else this
}
