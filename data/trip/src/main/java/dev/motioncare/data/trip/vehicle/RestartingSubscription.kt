package dev.motioncare.data.trip.vehicle

/**
 * Keeps one subscription alive across car service restarts. A crash drops every manager and its callbacks, and
 * the same Car connects again by itself when the service is back, so each ready subscribes on the manager it brings.
 */
internal class RestartingSubscription<M : Any>(
    private val subscribe: (M) -> Boolean,
    private val unsubscribe: (M) -> Unit,
    private val onLost: () -> Unit,
    private val onFailed: (Exception) -> Unit,
) {
    private var current: M? = null

    fun onReady(manager: M?) {
        if (manager == null || !subscribe(manager)) {
            onFailed(IllegalStateException("Car service subscription failed"))
            return
        }
        current = manager
    }

    /** The old manager is already gone, so there is nothing to unsubscribe from. */
    fun onLost() {
        current = null
        onLost.invoke()
    }

    fun close() {
        current?.let(unsubscribe)
        current = null
    }
}
