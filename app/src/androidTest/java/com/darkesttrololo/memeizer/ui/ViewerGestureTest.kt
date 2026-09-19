package com.darkesttrololo.memeizer.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.darkesttrololo.memeizer.MainActivity
import com.darkesttrololo.memeizer.R
import com.darkesttrololo.memeizer.data.search.SearchResult
import com.darkesttrololo.memeizer.ui.home.MemePreviewDialog
import com.darkesttrololo.memeizer.ui.home.ViewerSession
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Inject real multi-pointer input; no timing dependency on separate adb tap processes. */
@RunWith(AndroidJUnit4::class)
class ViewerGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation

    private fun zoom(percent: Int) = instrumentation.targetContext.getString(R.string.viewer_zoom, percent)

    @Test
    fun zoomBlocksPagingAndResetsWhenPageChanges() {
        val file = File(instrumentation.targetContext.cacheDir, "viewer-gesture-test.png")
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            val dismissals = AtomicInteger()
            val results = List(3) {
                SearchResult(
                    it.toLong(),
                    if (it == 2) File(file.parentFile, "missing-viewer-test.png").toURI().toString() else file.toURI().toString(),
                    "Long filename ".repeat(12) + it + ".png",
                    if (it == 1) "Длинный OCR text. ".repeat(200) else "",
                    null, null, null,
                )
            }
            instrumentation.runOnMainSync {
                activity.setContent {
                    MemePreviewDialog(ViewerSession(results, 0), onPageChanged = {}, onDismiss = { dismissals.incrementAndGet() })
                }
            }
            awaitNode { it.stateDescription?.toString() == zoom(100) }
            // Allow Coil to decode the tiny fixture before injecting gestures.
            SystemClock.sleep(1000)
            val bounds = Rect().also { rect ->
                awaitNode { it.stateDescription != null }.getBoundsInScreen(rect)
            }
            swipe(bounds.exactCenterX(), bounds.exactCenterY(), bounds.exactCenterX(), bounds.top + 60f)
            awaitNode { it.text?.toString() == instrumentation.targetContext.getString(R.string.viewer_no_text) }
            awaitNode { it.text?.toString() == results[0].displayName }
            swipe(bounds.exactCenterX(), bounds.bottom - 80f, bounds.exactCenterX(), bounds.bottom - bounds.height() * .35f)
            awaitNode { it.text?.toString() == instrumentation.targetContext.getString(R.string.viewer_unknown) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            SystemClock.sleep(400)
            awaitNode { it.text?.toString() == "1 / 3" }
            val x = bounds.exactCenterX()
            val y = bounds.exactCenterY()
            doubleTap(x, y)
            awaitNode { it.stateDescription?.toString() == zoom(200) }
            swipe(bounds.right - 40f, y, bounds.left + 40f, y)
            assertTrue(awaitNode { it.text?.toString() == "1 / 3" }.isVisibleToUser)
            doubleTap(x, y)
            awaitNode { it.stateDescription?.toString() == zoom(100) }
            pinch(x, y, 35f, 280f)
            awaitNode { it.stateDescription?.toString() == zoom(500) }
            pinch(x, y, 280f, 20f)
            awaitNode { it.stateDescription?.toString() == zoom(100) }
            // A second pointer cancels an unfinished upward drag and takes over zoom.
            pinch(x, y, 35f, 280f, leadInY = 120f)
            awaitNode { it.stateDescription != null && it.stateDescription.toString() != zoom(100) }
            pinch(x, y, 280f, 20f)
            awaitNode { it.stateDescription?.toString() == zoom(100) }
            // A mostly horizontal diagonal still pages.
            swipe(bounds.right - 40f, y, bounds.left + 40f, y + 100f)
            awaitNode { it.text?.toString() == "2 / 3" }
            assertEquals(zoom(100), awaitNode { it.stateDescription != null }.stateDescription.toString())
            // The open panel blocks both paging and double-tap zoom.
            swipe(x, y, x, bounds.top + 60f)
            swipe(bounds.right - 40f, bounds.top + 100f, bounds.left + 40f, bounds.top + 100f)
            awaitNode { it.text?.toString() == "2 / 3" }
            doubleTap(x, bounds.top + 100f)
            assertEquals(zoom(100), awaitNode { it.stateDescription != null }.stateDescription.toString())
            // A downward image gesture collapses details without dismissing the viewer.
            swipe(x, bounds.top + 100f, x, y)
            assertEquals(0, dismissals.get())
            // The first and last pages stop at their boundaries.
            repeat(3) { swipe(bounds.right - 40f, y, bounds.left + 40f, y) }
            awaitNode { it.text?.toString() == "3 / 3" }
            awaitNode { it.text?.toString() == instrumentation.targetContext.getString(R.string.viewer_unavailable) }
            repeat(3) { swipe(bounds.left + 40f, y, bounds.right - 40f, y) }
            awaitNode { it.text?.toString() == "1 / 3" }
            swipe(x, y, x, y + bounds.height() * .1f, stepMillis = 60)
            assertEquals(0, dismissals.get())
            doubleTap(x, y)
            awaitNode { it.stateDescription?.toString() == zoom(200) }
            swipe(x, y, x, bounds.bottom - 40f)
            assertEquals(0, dismissals.get())
            doubleTap(x, y)
            awaitNode { it.stateDescription?.toString() == zoom(100) }
            swipe(x, y, x, bounds.bottom - 40f, stepMillis = 60)
            assertEquals(1, dismissals.get())
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            SystemClock.sleep(400)
            assertEquals(1, dismissals.get())
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            file.delete()
        }
    }

    @Test
    fun fastShortSwipeDismissesOnceEvenWhenImageIsMissing() = withMissingPreview { bounds, dismissals ->
        swipe(bounds.exactCenterX(), bounds.exactCenterY(), bounds.exactCenterX(),
            bounds.exactCenterY() + bounds.height() * .2f, stepMillis = 2, steps = 4)
        awaitDismissal(dismissals)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        SystemClock.sleep(400)
        assertEquals(1, dismissals.get())
    }

    @Test
    fun backCollapsesInformationBeforeDismissing() = withMissingPreview { bounds, dismissals ->
        swipe(bounds.exactCenterX(), bounds.exactCenterY(), bounds.exactCenterX(), bounds.top + 60f)
        awaitNode { it.text?.toString() == instrumentation.targetContext.getString(R.string.viewer_info) }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        SystemClock.sleep(400)
        assertEquals(0, dismissals.get())
        awaitNode {
            it.refresh()
            it.stateDescription != null && Rect().also(it::getBoundsInScreen) == bounds
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        SystemClock.sleep(400)
        assertEquals(1, dismissals.get())
    }

    @Test
    fun accessibleInformationActionAndCloseButtonWorkWithoutImage() = withMissingPreview { _, dismissals ->
        val label = instrumentation.targetContext.getString(R.string.viewer_show_info)
        val node = awaitNode { it.actionList.any { action -> action.label?.toString() == label } }
        assertTrue(node.performAction(node.actionList.first { it.label?.toString() == label }.id))
        awaitNode { it.text?.toString() == instrumentation.targetContext.getString(R.string.viewer_info) }
        var close = awaitNode { it.contentDescription?.toString() == instrumentation.targetContext.getString(R.string.viewer_close) }
        while (!close.isClickable) close = close.parent ?: error("Missing close button")
        assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        awaitDismissal(dismissals)
    }

    @Test
    fun textScrollCollapsesOnlyAfterReachingItsStart() = withMissingPreview("OCR line\n".repeat(80)) { bounds, dismissals ->
        val x = bounds.exactCenterX()
        swipe(x, bounds.exactCenterY(), x, bounds.top + 60f)
        val panelTop = bounds.top + bounds.height() * .55f
        val reduced = Rect().also { rect ->
            awaitNode {
                it.refresh()
                it.stateDescription != null && Rect().also(it::getBoundsInScreen).height() < bounds.height() * .6f
            }.getBoundsInScreen(rect)
        }
        swipe(x, bounds.bottom - 80f, x, bounds.bottom - 530f, stepMillis = 60)
        swipe(x, panelTop + 180f, x, panelTop + 380f, stepMillis = 60)
        assertEquals(reduced, Rect().also { awaitNode { it.stateDescription != null }.getBoundsInScreen(it) })
        swipe(x, panelTop + 120f, x, bounds.bottom - 80f, stepMillis = 60)
        awaitNode {
            it.refresh()
            it.stateDescription != null && Rect().also(it::getBoundsInScreen) == bounds
        }
        assertEquals(0, dismissals.get())
    }

    private fun awaitDismissal(dismissals: AtomicInteger) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (dismissals.get() == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertEquals(1, dismissals.get())
    }

    private fun withMissingPreview(text: String = "", block: (Rect, AtomicInteger) -> Unit) {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        val dismissals = AtomicInteger()
        try {
            val result = SearchResult(0, "file:///missing-preview-test.png", "missing.png", text, null, null, null)
            instrumentation.runOnMainSync {
                activity.setContent {
                    MemePreviewDialog(ViewerSession(listOf(result), 0), {}, { dismissals.incrementAndGet() })
                }
            }
            awaitNode { it.text?.toString() == instrumentation.targetContext.getString(R.string.viewer_unavailable) }
            // Wait for the platform dialog enter transition and focus before injecting input.
            SystemClock.sleep(1000)
            val bounds = Rect().also { awaitNode { it.stateDescription != null }.getBoundsInScreen(it) }
            block(bounds, dismissals)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            find(automation.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Expected viewer node not found; zoom=${find(automation.rootInActiveWindow) { it.stateDescription != null }?.stateDescription}")
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null || !node.refresh()) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), predicate)?.let { return it }
        return null
    }

    private fun doubleTap(x: Float, y: Float) {
        repeat(2) {
            val down = SystemClock.uptimeMillis()
            event(down, MotionEvent.ACTION_DOWN, listOf(x to y))
            SystemClock.sleep(35)
            event(down, MotionEvent.ACTION_UP, listOf(x to y))
            SystemClock.sleep(50)
        }
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, stepMillis: Long = 20, steps: Int = 12) {
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, listOf(x1 to y1))
        repeat(steps) { step ->
            SystemClock.sleep(stepMillis)
            val fraction = (step + 1).toFloat() / steps
            event(down, MotionEvent.ACTION_MOVE, listOf((x1 + (x2 - x1) * fraction) to (y1 + (y2 - y1) * fraction)))
        }
        event(down, MotionEvent.ACTION_UP, listOf(x2 to y2))
        SystemClock.sleep(400)
    }

    private fun pinch(x: Float, y: Float, start: Float, end: Float, leadInY: Float = 0f) {
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, listOf((x - start) to (y + leadInY)))
        if (leadInY != 0f) repeat(8) { step ->
            SystemClock.sleep(20)
            event(down, MotionEvent.ACTION_MOVE, listOf((x - start) to (y + leadInY * (7 - step) / 8f)))
        }
        event(down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf((x - start) to y, (x + start) to y))
        repeat(20) { step ->
            SystemClock.sleep(20)
            val radius = start + (end - start) * (step + 1) / 20f
            event(down, MotionEvent.ACTION_MOVE, listOf((x - radius) to y, (x + radius) to y))
        }
        event(down, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf((x - end) to y, (x + end) to y))
        event(down, MotionEvent.ACTION_UP, listOf((x - end) to y))
    }

    private fun event(down: Long, action: Int, points: List<Pair<Float, Float>>) {
        val properties = Array(points.size) { index ->
            MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val coordinates = points.map { (x, y) ->
            MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f }
        }.toTypedArray()
        val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, points.size, properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
}
