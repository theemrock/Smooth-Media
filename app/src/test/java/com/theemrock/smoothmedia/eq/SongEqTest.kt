package com.theemrock.smoothmedia.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SongEqTest {
    private val n = EqBands24.COUNT
    private val flat = DoubleArray(n) { -30.0 }
    private val tilted = DoubleArray(n) { -30.0 + if (it == 6) 20.0 else 0.0 }
    private val windows = listOf(SpectrumWindow(1_000, tilted), SpectrumWindow(6_000, flat))
    private val overrideLevels = List(n) { if (it == 2) 300 else 0 }

    @Test
    fun overrideWinsOverEverything() {
        val plan = SongEqResolver.resolve(EqMode.AUTO, SongEqData(overrideLevels, windows))
        assertTrue(plan is SongEqPlan.Override)
        assertEquals(3.0, (plan as SongEqPlan.Override).profile.bands[2].gainDb, 1e-9)
    }

    @Test
    fun autoModeUsesDynamicWhenScannedAndLegacyWhenNot() {
        assertTrue(SongEqResolver.resolve(EqMode.AUTO, SongEqData(null, windows)) is SongEqPlan.Dynamic)
        assertSame(SongEqPlan.Legacy, SongEqResolver.resolve(EqMode.AUTO, SongEqData(null, null)))
        assertSame(SongEqPlan.Legacy, SongEqResolver.resolve(EqMode.AUTO, SongEqData(null, emptyList())))
        assertSame(SongEqPlan.Legacy, SongEqResolver.resolve(EqMode.AUTO, SongEqData(null, listOf(SpectrumWindow(0, null)))))
    }

    @Test
    fun manualModeIgnoresScannedWindows() {
        assertSame(SongEqPlan.Legacy, SongEqResolver.resolve(EqMode.MANUAL, SongEqData(null, windows)))
    }

    @Test
    fun wrongSizedOverrideIsIgnored() {
        assertSame(SongEqPlan.Legacy, SongEqResolver.resolve(EqMode.MANUAL, SongEqData(listOf(1, 2, 3), null)))
    }

    @Test
    fun sessionSlewsGainsAndSnapsAfterReset() {
        val plan = SongEqResolver.resolve(EqMode.AUTO, SongEqData(null, windows)) as SongEqPlan.Dynamic
        val session = DynamicSession(plan)
        val first = session.profileAt(1_000, 0.2)
        val later = session.profileAt(6_000, 0.2)
        val maxStep = DynamicEq.MAX_SLEW_DB_PER_SEC * 0.2 + 1e-9
        for (i in 0 until n) assertTrue(abs(later.bands[i].gainDb - first.bands[i].gainDb) <= maxStep)

        session.reset()
        val snapped = session.profileAt(6_000, 0.2)
        val target = DynamicEq.gainsAt(windows, 6_000)
        for (i in 0 until n) assertEquals(target[i], snapped.bands[i].gainDb, 1e-9)
    }

    @Test
    fun dynamicProfileCarriesSongPreamp() {
        val plan = SongEqResolver.resolve(EqMode.AUTO, SongEqData(null, windows)) as SongEqPlan.Dynamic
        assertTrue(plan.preampDb <= 0.0)
        assertEquals(plan.preampDb, DynamicSession(plan).profileAt(1_000, 0.2).preampDb, 1e-9)
    }
}
