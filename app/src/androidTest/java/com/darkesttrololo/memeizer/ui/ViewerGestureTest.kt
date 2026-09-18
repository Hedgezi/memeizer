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
import com.darkesttrololo.memeizer.data.search.SearchResult
import com.darkesttrololo.memeizer.ui.home.MemePreviewDialog
import com.darkesttrololo.memeizer.ui.home.ViewerSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Inject real multi-pointer input; no timing dependency on separate adb tap processes. */
@RunWith(AndroidJUnit4::class)
class ViewerGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation

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
                    MemePreviewDialog(ViewerSession(results, 0), onPageChanged = {}, onDismiss = {})
                }
            }
            awaitNode { it.stateDescription?.toString() == "Масштаб: 100%" }
            // Allow Coil to decode the tiny fixture before injecting gestures.
            SystemClock.sleep(1000)
            val bounds = Rect().also { rect ->
                awaitNode { it.stateDescription != null }.getBoundsInScreen(rect)
            }
            clickText("Информация")
            awaitNode { it.text?.toString() == "Нет распознанного текста" }
            awaitNode { it.text?.toString() == "Нет данных" }
            awaitNode { it.text?.toString() == results[0].displayName }
            swipe(bounds.exactCenterX(), bounds.bottom - 60f, bounds.exactCenterX(), bounds.top + 60f)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitNode { it.text?.toString() == "1 / 3" }
            val x = bounds.exactCenterX()
            val y = bounds.exactCenterY()
            doubleTap(x, y)
            awaitNode { it.stateDescription?.toString() == "Масштаб: 200%" }
            swipe(bounds.right - 40f, y, bounds.left + 40f, y)
            assertTrue(awaitNode { it.text?.toString() == "1 / 3" }.isVisibleToUser)
            doubleTap(x, y)
            awaitNode { it.stateDescription?.toString() == "Масштаб: 100%" }
            pinch(x, y, 35f, 280f)
            awaitNode { it.stateDescription?.toString() == "Масштаб: 500%" }
            pinch(x, y, 280f, 20f)
            awaitNode { it.stateDescription?.toString() == "Масштаб: 100%" }
            swipe(bounds.right - 40f, y, bounds.left + 40f, y)
            awaitNode { it.text?.toString() == "2 / 3" }
            assertEquals("Масштаб: 100%", awaitNode { it.stateDescription != null }.stateDescription.toString())
            // The first and last pages stop at their boundaries.
            repeat(3) { swipe(bounds.right - 40f, y, bounds.left + 40f, y) }
            awaitNode { it.text?.toString() == "3 / 3" }
            awaitNode { it.text?.toString()?.startsWith("Не удалось загрузить изображение") == true }
            repeat(3) { swipe(bounds.left + 40f, y, bounds.right - 40f, y) }
            awaitNode { it.text?.toString() == "1 / 3" }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            file.delete()
        }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            find(automation.rootInActiveWindow, predicate)?.let { return it }
            SystemClock.sleep(100)
        }
        error("Expected viewer node not found")
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) find(node.getChild(index), predicate)?.let { return it }
        return null
    }

    private fun clickText(text: String) {
        var node = awaitNode { it.text?.toString() == text }
        while (!node.isClickable) node = node.parent ?: error("No clickable parent for $text")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(500)
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

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, listOf(x1 to y1))
        repeat(12) { step ->
            SystemClock.sleep(20)
            val fraction = (step + 1) / 12f
            event(down, MotionEvent.ACTION_MOVE, listOf((x1 + (x2 - x1) * fraction) to (y1 + (y2 - y1) * fraction)))
        }
        event(down, MotionEvent.ACTION_UP, listOf(x2 to y2))
        SystemClock.sleep(400)
    }

    private fun pinch(x: Float, y: Float, start: Float, end: Float) {
        val down = SystemClock.uptimeMillis()
        event(down, MotionEvent.ACTION_DOWN, listOf((x - start) to y))
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
