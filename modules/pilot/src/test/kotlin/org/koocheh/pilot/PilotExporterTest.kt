package org.koocheh.pilot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PilotExporterTest {

    private class FakeSink : ShareSink {
        var shared = 0
        override fun share(fileName: String, mimeType: String, bytes: ByteArray) {
            shared++
        }
    }

    private val flags = ExperimentFlags.assign(5)

    private fun newExporter(recorder: PilotRecorder, sink: FakeSink = FakeSink()) =
        PilotExporter(recorder, flags, sink)

    @Test
    fun exporterRefusesWithoutConsent() = runBlocking {
        val sink = FakeSink()
        val exporter = newExporter(PilotRecorder(InMemoryPilotStore()), sink)
        for (c in listOf<Consent?>(null, Consent(granted = false, recordedOnDay = 0))) {
            try {
                exporter.exportJson(c)
                fail("export must be refused without consent")
            } catch (_: ConsentRequiredException) {
            }
        }
        assertEquals(0, sink.shared)
    }

    @Test
    fun exportContainsNoPeerIdentifiers() = runBlocking {
        val store = InMemoryPilotStore()
        val recorder = PilotRecorder(store)
        recorder.firstSignal(1)
        recorder.mutualHello(3)
        recorder.appOpened(3)
        recorder.batterySample(4.5f)
        recorder.recordRange(RangeSnapshot(listOf(RangeBucket(10, 4, 3), RangeBucket(30, 2, 1))))
        recorder.meetUp(true)
        val sink = FakeSink()
        val json = String(
            newExporter(recorder, sink).exportJson(Consent(granted = true, recordedOnDay = 0)),
            Charsets.UTF_8,
        )
        assertEquals(1, sink.shared)
        val allowed = setOf(
            "consentVersion", "consentDay", "flags", "familiarPrompts", "tieredDisclosure",
            "inviteTokens", "mutualHellosByWeek", "firstHelloDelayDays", "daysOpened",
            "retainedDay14", "batteryPercentPerDayByDay", "discoveryByMaxMeters", "attempts",
            "successes", "meetUpsYesByWeek", "meetUpsNoByWeek",
        )
        val quoted = Regex("\"([^\"]*)\"").findAll(json).map { it.groupValues[1] }.toList()
        assertTrue(quoted.isNotEmpty())
        for (q in quoted) {
            assertTrue("unexpected string in export: $q", q in allowed || q.toIntOrNull() != null)
        }
        // the store itself only ever holds counts keyed by day/week/bucket numbers
        assertFalse(store.all().keys.any { it.contains("peer", ignoreCase = true) })
    }

    @Test
    fun countersAggregateByWeek() {
        val recorder = PilotRecorder(InMemoryPilotStore())
        recorder.firstSignal(2)
        recorder.mutualHello(4)   // week 0
        recorder.mutualHello(6)   // week 0
        recorder.mutualHello(7)   // week 1
        recorder.mutualHello(20)  // week 2
        recorder.appOpened(14)
        recorder.meetUp(true)
        recorder.meetUp(false)
        val s = recorder.summary()
        assertEquals(mapOf(0 to 2, 1 to 1, 2 to 1), s.helloByWeek)
        assertEquals(2, s.firstHelloDelayDays)
        assertTrue(s.retainedDay14)
        assertEquals(mapOf(2 to 1), s.meetUpYesByWeek)
        assertEquals(mapOf(2 to 1), s.meetUpNoByWeek)
        assertNull(PilotRecorder(InMemoryPilotStore()).summary().firstHelloDelayDays)
    }
}
