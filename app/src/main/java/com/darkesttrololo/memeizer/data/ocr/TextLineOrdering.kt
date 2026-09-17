package com.darkesttrololo.memeizer.data.ocr

import com.equationl.ncnnandroidppocr.bean.OcrTextLineResult

internal fun sortTextLines(textLines: List<OcrTextLineResult>): List<OcrTextLineResult> {
    if (textLines.size < 2) return textLines

    val rowTolerance = textLines
        .mapNotNull { line ->
            val ys = line.points.map { it.y }
            (ys.maxOrNull() ?: return@mapNotNull null) - (ys.minOrNull() ?: return@mapNotNull null)
        }
        .sorted()
        .let { heights -> heights.getOrNull(heights.size / 2) ?: 32 }
        .coerceAtLeast(24) * 3 / 4

    val byY = textLines.sortedWith(compareBy<OcrTextLineResult> { it.centerY() }.thenBy { it.leftX() })
    val rows = mutableListOf<MutableList<OcrTextLineResult>>()
    for (line in byY) {
        val row = rows.lastOrNull()
        // Anchor to the first (topmost) line, preventing tolerance chains between rows.
        if (row == null || line.centerY() - row.first().centerY() > rowTolerance) {
            rows.add(mutableListOf(line))
        } else {
            row.add(line)
        }
    }
    return rows.flatMap { row -> row.sortedWith(compareBy<OcrTextLineResult> { it.leftX() }.thenBy { it.centerY() }) }
}

private fun OcrTextLineResult.centerY(): Double = points.map { it.y }.average().takeUnless { it.isNaN() } ?: 0.0
private fun OcrTextLineResult.leftX(): Int = points.minOfOrNull { it.x } ?: 0
