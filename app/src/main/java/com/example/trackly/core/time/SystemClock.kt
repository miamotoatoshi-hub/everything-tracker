package com.example.trackly.core.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Wall clock that always follows the device's *current* time zone. `Clock.systemDefaultZone()` captures
 * the zone once, which goes stale when the user travels while the process is alive.
 */
object SystemClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
