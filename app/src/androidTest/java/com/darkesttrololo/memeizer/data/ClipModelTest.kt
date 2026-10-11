package com.darkesttrololo.memeizer.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.room.Room
import androidx.room.withTransaction
import com.darkesttrololo.memeizer.data.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.darkesttrololo.memeizer.data.clip.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val assets get() = InstrumentationRegistry.getInstrumentation().context.assets
    private fun json(name: String) = assets.open("clip-reference/$name").bufferedReader().use { JSONArray(it.readText()) }
    private fun vector(array: JSONArray) = FloatArray(array.length()) { array.getDouble(it).toFloat() }

    @Test fun tokenizerMatchesPythonIncludingLongQueriesAndSpecialTokens() {
        val manifest = JSONObject(context.assets.open("clip/manifest.json").bufferedReader().use { it.readText() })
        val vocabulary = JSONObject(context.assets.open("clip/vocab.json").bufferedReader().use { it.readText() })
        val tokens = manifest.getJSONObject("tokens")
        val tokenizer = ClipTokenizer(vocabulary.keys().asSequence().associateWith { vocabulary.getInt(it) },
            context.assets.open("clip/merges.txt").bufferedReader().use { it.readLines() },
            tokens.getInt("bos"), tokens.getInt("eos"), tokens.getInt("pad"), tokens.getInt("length"))
        val cases = json("tokens.json")
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val actual = tokenizer.tokenize(case.getString("text"))
            assertArrayEquals(case.getString("text"), LongArray(77) { case.getJSONArray("ids").getLong(it) }, actual.ids)
            assertArrayEquals(LongArray(77) { case.getJSONArray("mask").getLong(it) }, actual.mask)
        }
    }

    @Test fun bicubicPreprocessingMatchesPythonPortraitLandscapeAndSquare() {
        for (name in listOf("portrait", "landscape", "square")) {
            val bitmap = assets.open("clip-reference/$name.png").use { BitmapFactory.decodeStream(it) }
            val argb = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val actual = ClipPreprocessor.pixels(argb, bitmap.width, bitmap.height, 224,
                floatArrayOf(.48145466f, .4578275f, .40821073f), floatArrayOf(.26862954f, .26130258f, .27577711f))
            bitmap.recycle()
            val bytes = assets.open("clip-reference/$name.pixels.bin").use { it.readBytes() }
            val reference = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val expected = FloatArray(reference.remaining()).also { reference.get(it) }
            assertArrayEquals(name, expected, actual, .016f) // <=1 quantized RGB level after normalization.
        }
    }

    @Test fun offlineEncodersMatchPythonAndRecordDeviceLatency() = runBlocking {
        val engine = OnnxClipEmbeddingEngine(context)
        var peakPssKb = 0
        val sampler = launch(Dispatchers.IO) {
            while (isActive) {
                val memory = android.os.Debug.MemoryInfo()
                android.os.Debug.getMemoryInfo(memory)
                peakPssKb = maxOf(peakPssKb, memory.totalPss)
                delay(25)
            }
        }
        val started = SystemClock.elapsedRealtimeNanos()
        engine.initialize()
        val coldMs = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
        val queries = json("tokens.json")
        val times = mutableListOf<Double>()
        for (index in 0 until queries.length()) {
            val case = queries.getJSONObject(index)
            val start = SystemClock.elapsedRealtimeNanos()
            val actual = engine.embedText(case.getString("text"))
            times.add((SystemClock.elapsedRealtimeNanos() - start) / 1e6)
            assertArrayEquals(case.getString("text"), vector(case.getJSONArray("embedding")), actual, .0002f)
        }
        val images = json("images.json")
        val imageTimes = mutableListOf<Double>()
        for (index in 0 until images.length()) {
            val case = images.getJSONObject(index)
            val file = File(context.cacheDir, case.getString("file"))
            assets.open("clip-reference/${file.name}").use { input -> file.outputStream().use { input.copyTo(it) } }
            try {
                val start = SystemClock.elapsedRealtimeNanos()
                val actual = engine.embedImage(Uri.fromFile(file))
                imageTimes.add((SystemClock.elapsedRealtimeNanos() - start) / 1e6)
                assertArrayEquals(file.name, vector(case.getJSONArray("embedding")), actual, .003f)
            } finally { file.delete() }
        }
        // Synthetic normalized vectors isolate exact retrieval cost; they do not measure relevance.
        val db = Room.inMemoryDatabaseBuilder(context, MemeizerDatabase::class.java).build()
        val searchTimes = mutableListOf<Double>()
        try {
            db.withTransaction {
                val folder = db.folderDao().insert(IndexedFolderEntity(treeUri = "content://benchmark", displayName = "Benchmark", createdAt = 1, updatedAt = 1))
                val random = java.util.Random(42)
                repeat(10_000) { index ->
                    val id = db.imageDao().insert(IndexedImageEntity(folderId = folder, uri = "content://benchmark/$index", documentKey = "$index", displayName = "$index", mimeType = "image/png", size = 1, lastModified = 1, contentKey = "$index", indexStatus = "FAILED", createdAt = 1, updatedAt = 1))
                    val embedding = ClipVectors.normalize(FloatArray(512) { random.nextFloat() * 2 - 1 }, 512)
                    db.clipDao().save(ClipEmbeddingEntity(id, engine.modelVersion, "$index", 512, ClipVectors.encode(embedding), "READY", null, 1))
                }
            }
            val repository = ClipRepository(db, engine)
            repository.search("a surprised cat").first() // Warm up query encoder and complete retrieval.
            repeat(20) {
                val start = SystemClock.elapsedRealtimeNanos()
                assertEquals(30, repository.search("a surprised cat").first().size)
                searchTimes.add((SystemClock.elapsedRealtimeNanos() - start) / 1e6)
            }
        } finally { db.close() }
        sampler.cancelAndJoin()
        val report = JSONObject().put("device", android.os.Build.MODEL).put("abi", android.os.Build.SUPPORTED_ABIS[0])
            .put("android", android.os.Build.VERSION.RELEASE).put("model", engine.modelVersion)
            .put("sampled_peak_pss_kb", peakPssKb).put("vectors", 10_000)
            .put("full_search_10k_ms", JSONArray(searchTimes)).put("p95_search_10k_ms", searchTimes.sorted()[18])
            .put("cold_initialize_ms", coldMs).put("text_ms", JSONArray(times)).put("image_ms", JSONArray(imageTimes))
        File(context.filesDir, "clip-device-measurements.json").writeText(report.toString(2))
        android.util.Log.i("ClipMeasurements", report.toString())
        engine.close()
        Unit
    }
}
