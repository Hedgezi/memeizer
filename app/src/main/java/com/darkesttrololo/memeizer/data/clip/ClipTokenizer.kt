package com.darkesttrololo.memeizer.data.clip

import java.text.Normalizer
import java.util.Locale

/** CLIP's byte-level BPE. Assets and special IDs come from the exported checkpoint. */
class ClipTokenizer(
    private val vocabulary: Map<String, Int>,
    merges: List<String>,
    private val bos: Int,
    private val eos: Int,
    private val pad: Int,
    private val length: Int = 77,
) {
    private val ranks = merges.filter { it.isNotBlank() && !it.startsWith("#") }
        .mapIndexed { index, line -> line to index }.toMap()
    private val byteEncoder: Map<Int, String> = run {
        val bytes = ((33..126) + (161..172) + (174..255)).toMutableList()
        val chars = bytes.toMutableList()
        var offset = 0
        for (byte in 0..255) if (byte !in bytes) {
            bytes.add(byte)
            chars.add(256 + offset++)
        }
        bytes.indices.associate { bytes[it] to chars[it].toChar().toString() }
    }
    private val pattern = Regex("<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+", RegexOption.IGNORE_CASE)

    data class Tokens(val ids: LongArray, val mask: LongArray)

    fun tokenize(text: String): Tokens {
        val clean = Normalizer.normalize(text, Normalizer.Form.NFC)
            .replace(Regex("[\\s\\p{Z}\\u0085\\u001c-\\u001f]+"), " ").trim().lowercase(Locale.ROOT)
        val pieces = ArrayList<Int>()
        pieces.add(bos)
        for (match in pattern.findAll(clean)) {
            val token = match.value
            if (token == "<|startoftext|>" || token == "<|endoftext|>") {
                pieces.add(vocabulary.getValue(token))
            } else {
                val encoded = token.toByteArray(Charsets.UTF_8).joinToString("") { byteEncoder.getValue(it.toInt() and 255) }
                bpe(encoded).forEach { pieces.add(vocabulary.getValue(it)) }
            }
            if (pieces.size >= length - 1) break
        }
        val tokens = pieces.take(length - 1) + eos
        return Tokens(
            LongArray(length) { tokens.getOrNull(it)?.toLong() ?: pad.toLong() },
            LongArray(length) { if (it < tokens.size) 1L else 0L },
        )
    }

    private fun bpe(token: String): List<String> {
        var word = token.map { it.toString() }.toMutableList()
        word[word.lastIndex] += "</w>"
        while (word.size > 1) {
            val pair = word.zipWithNext().minByOrNull { ranks["${it.first} ${it.second}"] ?: Int.MAX_VALUE } ?: break
            if ("${pair.first} ${pair.second}" !in ranks) break
            val merged = mutableListOf<String>()
            var index = 0
            while (index < word.size) {
                if (index + 1 < word.size && word[index] == pair.first && word[index + 1] == pair.second) {
                    merged.add(pair.first + pair.second)
                    index += 2
                } else merged.add(word[index++])
            }
            word = merged
        }
        return word
    }

}
