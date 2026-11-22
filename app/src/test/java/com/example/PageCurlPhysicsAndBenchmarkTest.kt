package com.example

import androidx.compose.ui.geometry.Offset
import com.example.data.preferences.CurlGrabMode
import com.example.ui.reader.CurlDirection
import com.example.ui.reader.CurlPhysicsMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlPhysicsAndBenchmarkTest {

    @Test
    fun grabZoneFilteringHonorsCornersAndEdgesPresets() {
        val w = 1000f
        val h = 1600f
        val center = Offset(500f, 800f)
        val rightEdgeMid = Offset(940f, 800f)
        val bottomRightCorner = Offset(940f, 1500f)

        assertTrue(CurlPhysicsMath.isGrabZoneAllowed(center, w, h, CurlGrabMode.CORNERS_AND_EDGES))
        assertFalse(CurlPhysicsMath.isGrabZoneAllowed(center, w, h, CurlGrabMode.EDGES_ONLY))
        assertTrue(CurlPhysicsMath.isGrabZoneAllowed(rightEdgeMid, w, h, CurlGrabMode.EDGES_ONLY))
        assertFalse(CurlPhysicsMath.isGrabZoneAllowed(rightEdgeMid, w, h, CurlGrabMode.CORNERS_ONLY))
        assertTrue(CurlPhysicsMath.isGrabZoneAllowed(bottomRightCorner, w, h, CurlGrabMode.CORNERS_ONLY))
    }

    @Test
    fun velocityAndThresholdCompletionRulesAreAccurate() {
        // High leftward flick completes forward turn even at low drag progress
        assertTrue(CurlPhysicsMath.shouldCompleteTurn(progress = 0.15f, velocityX = -900f, direction = CurlDirection.FORWARD, speedMultiplier = 1.0f))
        // High rightward cancel flick cancels forward turn even past midpoint
        assertFalse(CurlPhysicsMath.shouldCompleteTurn(progress = 0.55f, velocityX = 900f, direction = CurlDirection.FORWARD, speedMultiplier = 1.0f))
        // Slow drag beyond 42% completes turn; below 42% snaps back
        assertTrue(CurlPhysicsMath.shouldCompleteTurn(progress = 0.45f, velocityX = 0f, direction = CurlDirection.FORWARD, speedMultiplier = 1.0f))
        assertFalse(CurlPhysicsMath.shouldCompleteTurn(progress = 0.25f, velocityX = 0f, direction = CurlDirection.FORWARD, speedMultiplier = 1.0f))
        // Backward flick right completes backward turn
        assertTrue(CurlPhysicsMath.shouldCompleteTurn(progress = 0.18f, velocityX = 850f, direction = CurlDirection.BACKWARD, speedMultiplier = 1.0f))
    }

    @Test
    fun computeCurlFrameBenchmarkStaysUnder1MsPerFrame() {
        // Warmup JIT
        repeat(200) {
            CurlPhysicsMath.computeCurlFrame(
                width = 1080f,
                height = 2400f,
                progress = (it % 100) / 100f,
                anchorYFraction = 0.7f,
                direction = CurlDirection.FORWARD,
                curlStrength = 0.8f,
                shadowStrength = 0.65f
            )
        }

        val iterations = 1000
        val startNs = System.nanoTime()
        var acc = 0f
        for (i in 0 until iterations) {
            val frame = CurlPhysicsMath.computeCurlFrame(
                width = 1080f,
                height = 2400f,
                progress = i.toFloat() / iterations,
                anchorYFraction = 0.62f,
                direction = if (i % 2 == 0) CurlDirection.FORWARD else CurlDirection.BACKWARD,
                curlStrength = 0.78f,
                shadowStrength = 0.65f
            )
            acc += frame.creaseCenterX + frame.curlRadius + frame.shadowAlpha
        }
        val elapsedNs = System.nanoTime() - startNs
        val avgNs = elapsedNs / iterations
        assertTrue("Expected accumulated geometry > 0, got $acc", acc > 0f)
        assertTrue("Curl frame math must stay < 1ms/frame (was ${avgNs / 1_000_000.0} ms)", avgNs < 1_000_000L)
    }
}
