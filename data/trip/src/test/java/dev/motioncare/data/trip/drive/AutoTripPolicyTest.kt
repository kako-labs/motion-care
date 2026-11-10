package dev.motioncare.data.trip.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoTripPolicyTest {
    private val policy = AutoTripPolicy(parkedFinishMs = 180_000)

    @Test fun `leaving park starts a trip`() {
        assertEquals(AutoAction.None, policy.onGear(Gear.PARK, 0, recording = false))
        assertEquals(AutoAction.Start, policy.onGear(Gear.DRIVE, 1_000, recording = false))
    }

    @Test fun `reverse also starts a trip`() {
        policy.onGear(Gear.PARK, 0, recording = false)
        assertEquals(AutoAction.Start, policy.onGear(Gear.REVERSE, 1_000, recording = false))
    }

    @Test fun `a car already in drive when watching begins starts a trip`() {
        assertEquals(AutoAction.Start, policy.onGear(Gear.DRIVE, 0, recording = false))
    }

    @Test fun `moving between drive and reverse does not start another trip`() {
        policy.onGear(Gear.PARK, 0, recording = false)
        policy.onGear(Gear.REVERSE, 1_000, recording = false)
        assertEquals(AutoAction.None, policy.onGear(Gear.DRIVE, 5_000, recording = true))
        assertEquals(AutoAction.None, policy.onGear(Gear.REVERSE, 9_000, recording = true))
    }

    @Test fun `a trip already recording is not started again`() {
        policy.onGear(Gear.PARK, 0, recording = true)
        assertEquals(AutoAction.None, policy.onGear(Gear.DRIVE, 1_000, recording = true))
    }

    @Test fun `a trip the driver ended in drive starts again only after park`() {
        policy.onGear(Gear.DRIVE, 0, recording = false)
        assertEquals(AutoAction.None, policy.onGear(Gear.NEUTRAL, 10_000, recording = false))
        assertEquals(AutoAction.None, policy.onGear(Gear.DRIVE, 20_000, recording = false))
        policy.onGear(Gear.PARK, 30_000, recording = false)
        assertEquals(AutoAction.Start, policy.onGear(Gear.DRIVE, 40_000, recording = false))
    }

    @Test fun `three minutes in park finishes the trip once`() {
        policy.onGear(Gear.DRIVE, 0, recording = false)
        policy.onGear(Gear.PARK, 60_000, recording = true)
        assertEquals(AutoAction.None, policy.onTick(239_999, recording = true))
        assertEquals(AutoAction.Finish(parkedAtMs = 60_000), policy.onTick(240_000, recording = true))
        assertEquals(AutoAction.None, policy.onTick(300_000, recording = false))
    }

    @Test fun `a short stop in park keeps the trip and restarts the wait`() {
        policy.onGear(Gear.DRIVE, 0, recording = false)
        policy.onGear(Gear.PARK, 60_000, recording = true)
        assertEquals(AutoAction.None, policy.onGear(Gear.DRIVE, 200_000, recording = true))
        assertEquals(AutoAction.None, policy.onTick(250_000, recording = true))
        policy.onGear(Gear.PARK, 300_000, recording = true)
        assertEquals(AutoAction.None, policy.onTick(479_999, recording = true))
        assertEquals(AutoAction.Finish(parkedAtMs = 300_000), policy.onTick(480_000, recording = true))
    }

    @Test fun `repeated park readings keep the first park time`() {
        policy.onGear(Gear.DRIVE, 0, recording = false)
        policy.onGear(Gear.PARK, 60_000, recording = true)
        policy.onGear(Gear.PARK, 120_000, recording = true)
        assertEquals(AutoAction.Finish(parkedAtMs = 60_000), policy.onTick(240_000, recording = true))
    }

    @Test fun `neutral neither starts nor finishes a trip`() {
        policy.onGear(Gear.DRIVE, 0, recording = false)
        policy.onGear(Gear.PARK, 60_000, recording = true)
        assertEquals(AutoAction.None, policy.onGear(Gear.NEUTRAL, 100_000, recording = true))
        assertEquals(AutoAction.None, policy.onTick(400_000, recording = true))
    }

    @Test fun `a lost gear signal keeps the park wait`() {
        policy.onGear(Gear.DRIVE, 0, recording = false)
        policy.onGear(Gear.PARK, 60_000, recording = true)
        assertEquals(AutoAction.None, policy.onGear(Gear.UNKNOWN, 100_000, recording = true))
        assertEquals(AutoAction.Finish(parkedAtMs = 60_000), policy.onTick(240_000, recording = true))
    }

    // After the head unit sleeps in park, the drive gear may arrive before the next tick.
    @Test fun `leaving a long park while still recording ends the old trip and starts a new one`() {
        policy.onGear(Gear.DRIVE, 0, recording = false)
        policy.onGear(Gear.PARK, 60_000, recording = true)
        assertEquals(AutoAction.Restart(parkedAtMs = 60_000), policy.onGear(Gear.DRIVE, 10_000_000, recording = true))
        assertEquals(AutoAction.None, policy.onTick(10_100_000, recording = true))
    }

    @Test fun `gear values follow the vehicle gear constants`() {
        assertEquals(Gear.PARK, gearOf(0x4))
        assertEquals(Gear.REVERSE, gearOf(0x2))
        assertEquals(Gear.NEUTRAL, gearOf(0x1))
        assertEquals(Gear.DRIVE, gearOf(0x8))
        assertEquals(Gear.DRIVE, gearOf(0x10))
        assertEquals(Gear.DRIVE, gearOf(0x200))
        assertEquals(Gear.UNKNOWN, gearOf(0))
        assertEquals(Gear.UNKNOWN, gearOf(null))
    }
}
