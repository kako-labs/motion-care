package dev.motioncare.data.trip.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartingSubscriptionTest {
    private class Manager(val name: String)

    private val subscribed = mutableListOf<String>()
    private val unsubscribed = mutableListOf<String>()
    private var lost = 0
    private val failures = mutableListOf<Exception>()
    private var accepts = true

    private val subscription = RestartingSubscription<Manager>(
        subscribe = { subscribed += it.name; accepts },
        unsubscribe = { unsubscribed += it.name },
        onLost = { lost++ },
        onFailed = { failures += it },
    )

    @Test fun `subscribes when the car service is ready`() {
        subscription.onReady(Manager("first"))

        assertEquals(listOf("first"), subscribed)
        assertTrue(failures.isEmpty())
    }

    @Test fun `marks the signal lost when the car service goes away`() {
        subscription.onReady(Manager("first"))
        subscription.onLost()

        assertEquals(1, lost)
    }

    @Test fun `subscribes again on the new manager after the car service restarts`() {
        subscription.onReady(Manager("first"))
        subscription.onLost()
        subscription.onReady(Manager("second"))

        assertEquals(listOf("first", "second"), subscribed)
        assertTrue(failures.isEmpty())
    }

    @Test fun `closing unsubscribes from the current manager only`() {
        subscription.onReady(Manager("first"))
        subscription.onLost()
        subscription.onReady(Manager("second"))
        subscription.close()

        assertEquals(listOf("second"), unsubscribed)
    }

    @Test fun `closing while the car service is gone unsubscribes nothing`() {
        subscription.onReady(Manager("first"))
        subscription.onLost()
        subscription.close()

        assertTrue(unsubscribed.isEmpty())
    }

    @Test fun `a refused subscription fails`() {
        accepts = false
        subscription.onReady(Manager("first"))

        assertEquals(1, failures.size)
        subscription.close()
        assertTrue(unsubscribed.isEmpty())
    }

    @Test fun `a missing manager fails`() {
        subscription.onReady(null)

        assertTrue(subscribed.isEmpty())
        assertEquals(1, failures.size)
    }
}
