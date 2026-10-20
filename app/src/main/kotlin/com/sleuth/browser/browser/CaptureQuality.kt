package com.sleuth.browser.browser

/**
 * A page that hides itself on purpose often leaves a flat colour behind. The
 * capture is checked for that so a blank picture never passes for evidence.
 */
object CaptureQuality {

    fun isUniform(samples: List<Int>): Boolean {
        if (samples.size < 2) return true
        val first = samples[0]
        for (sample in samples) {
            if (sample != first) return false
        }
        return true
    }
}
