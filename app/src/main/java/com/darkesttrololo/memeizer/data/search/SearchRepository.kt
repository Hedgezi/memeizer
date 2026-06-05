package com.darkesttrololo.memeizer.data.search

import com.darkesttrololo.memeizer.data.db.SearchDao
import com.darkesttrololo.memeizer.data.db.SearchResultRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SearchRepository(private val searchDao: SearchDao) {
    fun search(query: String): Flow<List<SearchResult>> {
        val normalized = query.lowercase().trim()
        if (normalized.isBlank()) return searchDao.observeGallery(GALLERY_LIMIT).mapSearchRows()
        val ftsQuery = toFtsQuery(normalized)
        if (ftsQuery.isBlank()) return flowOf(emptyList())

        return searchDao.search(ftsQuery, SEARCH_LIMIT).mapSearchRows()
    }

    private fun Flow<List<SearchResultRow>>.mapSearchRows(): Flow<List<SearchResult>> =
        map { rows ->
            rows.map { row ->
                SearchResult(
                    imageId = row.id,
                    uri = row.uri,
                    displayName = row.displayName,
                    text = row.text,
                )
            }
        }

    private fun toFtsQuery(query: String): String = query
        .replace(Regex("[^\\p{L}\\p{N}\\s]+"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(separator = " ") { "$it*" }

    private companion object {
        const val GALLERY_LIMIT = 500
        const val SEARCH_LIMIT = 100
    }
}

data class SearchResult(
    val imageId: Long,
    val uri: String,
    val displayName: String,
    val text: String,
)
