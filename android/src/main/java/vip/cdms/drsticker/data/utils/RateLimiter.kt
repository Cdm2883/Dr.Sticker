package vip.cdms.drsticker.data.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.TimeSource

class RateLimiter(
    private val limit: Int,
    private val interval: Duration
) {
    @Volatile
    var bypass = false
    private val calls = ArrayDeque<TimeSource.Monotonic.ValueTimeMark>()
    private val mutex = Mutex()

    suspend fun wait() {
        if (bypass) return
        val waitTime = mutex.withLock {
            if (bypass) return@withLock Duration.ZERO

            val now = TimeSource.Monotonic.markNow()
            while (calls.isNotEmpty() && (now - calls.first()) >= interval) {
                calls.removeFirst()
            }

            if (calls.size < limit) {
                calls.addLast(now)
                Duration.ZERO
            } else {
                val scheduledTime = calls.first() + interval
                calls.removeFirst()
                calls.addLast(scheduledTime)
                scheduledTime - now
            }
        }
        if (waitTime > Duration.ZERO) {
            delay(waitTime)
        }
    }
}
