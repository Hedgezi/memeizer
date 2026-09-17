package com.darkesttrololo.memeizer.data

import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darkesttrololo.memeizer.data.folder.FolderScanner
import com.darkesttrololo.memeizer.data.ocr.sortTextLines
import com.equationl.ncnnandroidppocr.bean.OcrTextLineResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

@RunWith(AndroidJUnit4::class)
class ScannerAndOrderingTest {
    private val tree = Uri.parse("content://test/tree/root")

    @Test fun scannerTraversesNestedFoldersAndRecognizesEmptyFolder() = runBlocking {
        val scanner = FolderScanner { uri, columns ->
            MatrixCursor(columns).apply {
                if (DocumentsContract.getDocumentId(uri) == "root") {
                    addRow(arrayOf("sub", "sub", DocumentsContract.Document.MIME_TYPE_DIR, null, null))
                    addRow(arrayOf("ignored", "text", "text/plain", 1, 1))
                } else {
                    addRow(arrayOf("picture", "pic", "image/png", 42, 10))
                }
            }
        }
        val result = scanner.scan(tree).single()
        assertEquals("pic", result.displayName)
        assertEquals(42L, result.size)
        assertTrue(FolderScanner { _, columns -> MatrixCursor(columns) }.scan(tree).isEmpty())
    }

    @Test fun scannerRejectsNullLoadingErrorAndPartialTraversal() = runBlocking {
        val scanners = listOf(
            FolderScanner { _, _ -> null },
            FolderScanner { _, columns -> MatrixCursor(columns).apply {
                extras = Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, true) }
            } },
            FolderScanner { _, columns -> MatrixCursor(columns).apply {
                extras = Bundle().apply { putString(DocumentsContract.EXTRA_ERROR, "unavailable") }
            } },
            FolderScanner { uri, columns ->
                if (DocumentsContract.getDocumentId(uri) != "root") throw SecurityException("lost permission")
                MatrixCursor(columns).apply {
                    addRow(arrayOf("picture", "pic", "image/png", 42, 10))
                    addRow(arrayOf("sub", "sub", DocumentsContract.Document.MIME_TYPE_DIR, null, null))
                }
            },
        )
        for (scanner in scanners) {
            try { scanner.scan(tree); fail("Must not publish an incomplete snapshot") } catch (_: IllegalStateException) {
            } catch (_: SecurityException) { }
        }
    }

    private fun line(name: String, x: Int, y: Int) = OcrTextLineResult(
        listOf(Point(x, y - 12), Point(x + 10, y - 12), Point(x + 10, y + 12), Point(x, y + 12)),
        name, 1f, 0, emptyList(),
    )

    @Test fun orderingHandlesToleranceCycleRowsColumnsEmptyAndSingle() {
        assertTrue(sortTextLines(emptyList()).isEmpty())
        val a = line("A", 30, 20)
        assertEquals(listOf(a), sortTextLines(listOf(a)))
        assertEquals(listOf("B", "A", "C"), sortTextLines(listOf(a, line("B", 20, 35), line("C", 10, 50))).map { it.text })
        val grid = listOf(line("bottomRight", 200, 100), line("topRight", 200, 20), line("bottomLeft", 10, 100), line("topLeft", 10, 22))
        assertEquals(listOf("topLeft", "topRight", "bottomLeft", "bottomRight"), sortTextLines(grid).map { it.text })
    }

    @Test fun randomizedOrderingPreservesAllLinesAndIsIdempotent() {
        val random = Random(0)
        repeat(100) {
            val lines = List(100) { index -> line("$index", random.nextInt(300), 20 + random.nextInt(150)) }
            val sorted = sortTextLines(lines)
            assertEquals(lines.toSet(), sorted.toSet())
            assertEquals(sorted, sortTextLines(sorted))
        }
    }
}
