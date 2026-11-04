package org.koocheh.pilot

/** Hands the exported file to the system share sheet. Implemented by the app; never uploads. */
interface ShareSink {
    fun share(fileName: String, mimeType: String, bytes: ByteArray)
}

/**
 * Manual export of the local pilot counters. Runs only when the participant triggers it
 * and only with a recorded consent object. The file leaves the device only through the
 * system share sheet, by the participant's own hand. No network code.
 */
class PilotExporter(
    private val recorder: PilotRecorder,
    private val flags: ExperimentFlags,
    private val shareSink: ShareSink,
) {
    suspend fun exportJson(consent: Consent?): ByteArray {
        if (consent == null || !consent.granted) throw ConsentRequiredException()
        val bytes = render(recorder.summary(), flags, consent).toByteArray(Charsets.UTF_8)
        shareSink.share(FILE_NAME, "application/json", bytes)
        return bytes
    }

    internal fun render(s: PilotSummary, f: ExperimentFlags, c: Consent): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"consentVersion\":${c.version},")
        sb.append("\"consentDay\":${c.recordedOnDay},")
        sb.append("\"flags\":{\"familiarPrompts\":${f.familiarPrompts},")
        sb.append("\"tieredDisclosure\":${f.tieredDisclosure},\"inviteTokens\":${f.inviteTokens}},")
        sb.append("\"mutualHellosByWeek\":${obj(s.helloByWeek)},")
        sb.append("\"firstHelloDelayDays\":${s.firstHelloDelayDays ?: "null"},")
        sb.append("\"daysOpened\":${s.daysOpened},")
        sb.append("\"retainedDay14\":${s.retainedDay14},")
        sb.append("\"batteryPercentPerDayByDay\":${obj(s.batteryAvgPercentPerDayByDay)},")
        val ranges = s.rangeBuckets.entries.joinToString(",", "{", "}") {
            "\"${it.key}\":{\"attempts\":${it.value.attempts},\"successes\":${it.value.successes}}"
        }
        sb.append("\"discoveryByMaxMeters\":$ranges,")
        sb.append("\"meetUpsYesByWeek\":${obj(s.meetUpYesByWeek)},")
        sb.append("\"meetUpsNoByWeek\":${obj(s.meetUpNoByWeek)}")
        sb.append("}")
        return sb.toString()
    }

    private fun obj(m: Map<Int, Number>): String =
        m.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }

    companion object {
        const val FILE_NAME = "koocheh-pilot-export.json"
    }
}
