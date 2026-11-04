package org.koocheh.transport

/** One pilot measurement: signal strength vs. user-entered distance bucket. */
data class RangeSample(val rssiDbm: Int, val distanceBucketMeters: Int)

/**
 * In-memory ring buffer for range measurements. Never persisted here;
 * the pilot module exports snapshot() on request. Holds no identifiers.
 */
class RangeLog(private val capacity: Int = 2_000) {
    private val buffer = ArrayDeque<RangeSample>()

    init {
        require(capacity > 0)
    }

    fun record(rssiDbm: Int, distanceBucketMeters: Int) {
        synchronized(buffer) {
            if (buffer.size == capacity) buffer.removeFirst()
            buffer.addLast(RangeSample(rssiDbm, distanceBucketMeters))
        }
    }

    fun snapshot(): List<RangeSample> = synchronized(buffer) { buffer.toList() }

    fun clear() = synchronized(buffer) { buffer.clear() }
}
