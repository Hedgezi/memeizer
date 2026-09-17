package com.darkesttrololo.memeizer

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.darkesttrololo.memeizer.data.indexing.IndexWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Shell-only diagnostics, compiled exclusively into debug builds. */
class DebugBackendProvider : ContentProvider() {
    private val app get() = requireNotNull(context).applicationContext as MemeizerApp
    private val db get() = app.container.database
    private val workManager get() = WorkManager.getInstance(app)

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        // ContentProvider.call does not enforce the manifest's read/write permissions.
        requireNotNull(context).enforceCallingPermission("android.permission.DUMP", "ADB diagnostics only")
        val response = try {
            runBlocking(Dispatchers.IO) {
                withTimeout(15_000) {
                    val input = JSONObject(String(Base64.decode(arg ?: "e30=", Base64.DEFAULT), Charsets.UTF_8))
                    JSONObject().put("ok", true).put("data", execute(method, input))
                }
            }
        } catch (error: Exception) {
            JSONObject().put("ok", false).put("error", "${error.javaClass.simpleName}: ${error.message}")
        }
        return Bundle().apply {
            putString("result", Base64.encodeToString(response.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        }
    }

    private suspend fun execute(method: String, input: JSONObject): JSONObject = when (method) {
        "status" -> {
            val grants = app.contentResolver.persistedUriPermissions
            val folders = rows("SELECT * FROM indexed_folders ORDER BY id")
            for (i in 0 until folders.length()) {
                val folder = folders.getJSONObject(i)
                folder.put("persisted_read_permission", grants.any {
                    it.uri.toString() == folder.getString("tree_uri") && it.isReadPermission
                })
            }
            JSONObject().put("folders", folders)
                .put("images", rows("SELECT active, index_status, COUNT(*) AS count FROM indexed_images GROUP BY active, index_status"))
                .put("manual_work", workList(IndexWorker.MANUAL_WORK_NAME))
                .put("periodic_work", workList(IndexWorker.PERIODIC_WORK_NAME))
        }
        "search" -> {
            val query = input.getString("query")
            val results = app.container.searchRepository.search(query).first()
            JSONObject().put("query", query).put("count", results.size).put("results", JSONArray().apply {
                results.forEach { result ->
                    put(JSONObject().put("image_id", result.imageId).put("uri", result.uri)
                        .put("display_name", result.displayName).put("text", result.text))
                }
            })
        }
        "image" -> {
            val id = input.getLong("id")
            require(id > 0) { "Image ID must be positive" }
            val images = rows("SELECT * FROM indexed_images WHERE id = ?", arrayOf(id))
            require(images.length() == 1) { "Image $id not found" }
            JSONObject().put("image", images.getJSONObject(0))
                .put("ocr", rows("SELECT * FROM ocr_results WHERE image_id = ?", arrayOf(id)))
                .put("fts", rows("SELECT rowid, image_id, text FROM meme_search_fts WHERE image_id = ?", arrayOf(id)))
        }
        "index" -> {
            // APPEND_OR_REPLACE preserves existing work and gives every call its own observable ID.
            val request = OneTimeWorkRequestBuilder<IndexWorker>()
                .setInputData(workDataOf(IndexWorker.KEY_FORCE_REINDEX to input.optBoolean("force", false)))
                .build()
            workManager.enqueueUniqueWork(IndexWorker.MANUAL_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                .result.get(10, TimeUnit.SECONDS)
            JSONObject().put("work_id", request.id.toString()).put("state", "ENQUEUED")
        }
        "work" -> {
            val id = UUID.fromString(input.getString("work_id"))
            val info = workManager.getWorkInfoById(id).get(10, TimeUnit.SECONDS)
            requireNotNull(info) { "Work $id not found" }
            workJson(info)
        }
        else -> throw IllegalArgumentException("Unknown method: $method")
    }

    private fun workList(name: String) = JSONArray().apply {
        workManager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS).forEach { put(workJson(it)) }
    }

    private fun workJson(info: WorkInfo) = JSONObject()
        .put("work_id", info.id.toString()).put("state", info.state.name)
        .put("run_attempt_count", info.runAttemptCount)
        .put("error", info.outputData.getString("error") ?: JSONObject.NULL)

    private fun rows(sql: String, args: Array<out Any?> = emptyArray()): JSONArray =
        db.openHelper.readableDatabase.query(sql, args).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    put(JSONObject().apply {
                        cursor.columnNames.forEachIndexed { index, name ->
                            put(name, when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                                else -> cursor.getString(index)
                            })
                        }
                    })
                }
            }
        }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
