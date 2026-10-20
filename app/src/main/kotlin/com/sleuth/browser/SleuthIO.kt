package com.sleuth.browser

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * All database and disk work happens on one background thread. The browser
 * thread only ever renders, so a page load is never held up by a write.
 */
object SleuthIO {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sleuth-io").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    private val main = Handler(Looper.getMainLooper())

    /** Fire and forget work. Failures are swallowed on purpose. */
    fun execute(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (error: Throwable) {
                // a failed background write must never take the browser down
            }
        }
    }

    /** Runs [block] in the background and hands the result back on the main thread. */
    fun <T> submit(block: () -> T, onResult: (T) -> Unit) {
        executor.execute {
            val value = try {
                block()
            } catch (error: Throwable) {
                null
            }
            if (value != null) {
                main.post { onResult(value) }
            }
        }
    }
}
