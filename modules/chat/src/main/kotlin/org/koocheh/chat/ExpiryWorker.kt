package org.koocheh.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Local scheduled purge of expired messages. Purely local: it posts no
 * notification and touches no message content.
 */
class ExpiryWorker(
    private val service: ChatService,
    private val intervalMs: Long = 60_000,
) {
    private var job: Job? = null

    @Synchronized
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try {
                    service.purgeExpired()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // retry on the next tick; nothing about content is logged
                }
                delay(intervalMs)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }
}
