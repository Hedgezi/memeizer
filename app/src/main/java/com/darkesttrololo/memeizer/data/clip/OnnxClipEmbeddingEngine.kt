package com.darkesttrololo.memeizer.data.clip

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class OnnxClipEmbeddingEngine(context: Context) : ClipEmbeddingEngine {
    private val context = context.applicationContext
    private val mutex = Mutex()
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private var vision: OrtSession? = null
    private var text: OrtSession? = null
    private var tokenizer: ClipTokenizer? = null
    private var imageSize = 224
    private var mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
    private var std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
    // Kept small and lazy: manifest parsing does not load either encoder.
    private val manifest by lazy {
        try { JSONObject(context.assets.open("clip/manifest.json").bufferedReader().use { it.readText() }) }
        catch (error: Exception) { throw ClipModelException("CLIP model assets are missing or invalid", error) }
    }
    override val modelVersion: String get() = manifest.getString("model_version")
    override val dimension: Int get() = manifest.getInt("dimension")

    override suspend fun initialize() = withContext(Dispatchers.IO) { mutex.withLock { load() } }

    private fun load() {
        if (vision != null && text != null) return
        var newVision: OrtSession? = null
        var newText: OrtSession? = null
        try {
            val files = manifest.getJSONObject("files")
            val cache = File(context.noBackupFilesDir, "clip/$modelVersion").apply { mkdirs() }
            fun asset(name: String): File {
                val expected = files.getString(name)
                val file = File(cache, name)
                if (file.isFile && sha256(file) == expected) return file
                val temporary = File(cache, "$name.tmp")
                context.assets.open("clip/$name").use { input -> temporary.outputStream().use { input.copyTo(it) } }
                if (sha256(temporary) != expected) {
                    temporary.delete()
                    throw ClipModelException("CLIP asset checksum mismatch: $name")
                }
                check(temporary.renameTo(file)) { "Cannot install CLIP asset: $name" }
                return file
            }
            val vocabJson = JSONObject(asset("vocab.json").readText())
            val vocab = vocabJson.keys().asSequence().associateWith { vocabJson.getInt(it) }
            val tokens = manifest.getJSONObject("tokens")
            val newTokenizer = ClipTokenizer(vocab, asset("merges.txt").readLines(),
                tokens.getInt("bos"), tokens.getInt("eos"), tokens.getInt("pad"), tokens.getInt("length"))
            val processor = manifest.getJSONObject("preprocessing")
            imageSize = processor.getInt("size")
            mean = FloatArray(3) { processor.getJSONArray("mean").getDouble(it).toFloat() }
            std = FloatArray(3) { processor.getJSONArray("std").getDouble(it).toFloat() }
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                newVision = environment.createSession(asset("vision.onnx").absolutePath, options)
                newText = environment.createSession(asset("text.onnx").absolutePath, options)
            }
            vision = newVision
            text = newText
            tokenizer = newTokenizer
        } catch (error: Exception) {
            newVision?.close()
            newText?.close()
            throw ClipModelException("Failed to load local CLIP encoders: ${error.message}", error)
        }
    }

    override suspend fun embedImage(uri: Uri): FloatArray = withContext(Dispatchers.IO) {
        initialize()
        val bitmap = ClipImageDecoder.decode(ImageDecoder.createSource(context.contentResolver, uri))
        val input = try {
            val argb = IntArray((bitmap.width.toLong() * bitmap.height).toInt())
            bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            ClipPreprocessor.pixels(argb, bitmap.width, bitmap.height, imageSize, mean, std)
        } finally { bitmap.recycle() }
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            load()
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 3, imageSize.toLong(), imageSize.toLong())).use { tensor ->
                run(vision!!, mapOf("pixel_values" to tensor))
            }
        }
    }

    override suspend fun embedText(text: String): FloatArray = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            load()
            val tokens = tokenizer!!.tokenize(text)
            val shape = longArrayOf(1, tokens.ids.size.toLong())
            OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.ids), shape).use { ids ->
                OnnxTensor.createTensor(environment, LongBuffer.wrap(tokens.mask), shape).use { mask ->
                    run(this@OnnxClipEmbeddingEngine.text!!, mapOf("input_ids" to ids, "attention_mask" to mask))
                }
            }
        }
    }

    private suspend fun run(session: OrtSession, input: Map<String, OnnxTensor>): FloatArray {
        try {
            session.run(input).use { result ->
                val tensor = result[0] as OnnxTensor
                val buffer = tensor.floatBuffer
                val vector = FloatArray(buffer.remaining()).also { buffer.get(it) }
                currentCoroutineContext().ensureActive()
                return ClipVectors.normalize(vector, dimension)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { throw ClipModelException("CLIP inference failed: ${error.message}", error) }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            vision?.close()
            text?.close()
            vision = null
            text = null
            tokenizer = null
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
