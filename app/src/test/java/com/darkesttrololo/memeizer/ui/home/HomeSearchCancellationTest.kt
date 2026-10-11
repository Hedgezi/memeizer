package com.darkesttrololo.memeizer.ui.home

import com.darkesttrololo.memeizer.data.search.SearchMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class HomeSearchCancellationTest {
    @Test fun modeSwitchCancelsRunningSemanticRequestAndEmitsOnlyTextSnapshot() = runBlocking {
        val request = MutableStateFlow(SearchRequest("cat", SearchMode.SEMANTIC))
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val snapshots = mutableListOf<SearchSnapshot>()
        val job = launch {
            homeSearchSnapshots(request, { _, mode ->
                if (mode == SearchMode.SEMANTIC) flow {
                    started.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                } else flowOf(emptyList())
            }, debounceMillis = 0).collect { snapshots.add(it) }
        }
        withTimeout(2_000) { started.await() }
        request.value = SearchRequest("cat", SearchMode.TEXT)
        withTimeout(2_000) {
            cancelled.await()
            while (snapshots.isEmpty()) yield()
        }
        assertEquals(listOf(SearchMode.TEXT), snapshots.map { it.request.mode })
        job.cancelAndJoin()
    }

    @Test fun newQueryCancelsDebounceBeforeOldQueryReachesRepository() = runBlocking {
        val request = MutableStateFlow(SearchRequest("first", SearchMode.SEMANTIC))
        val calls = mutableListOf<String>()
        val result = async {
            homeSearchSnapshots(request, { query, _ -> calls.add(query); flowOf(emptyList()) }, 100).first()
        }
        delay(25)
        request.value = SearchRequest("second", SearchMode.SEMANTIC)
        assertEquals("second", withTimeout(2_000) { result.await() }.request.query)
        assertEquals(listOf("second"), calls)
    }

    @Test fun blankQueryReturnsGalleryWithoutDebounce() = runBlocking {
        val snapshot = withTimeout(100) {
            homeSearchSnapshots(flowOf(SearchRequest("", SearchMode.SEMANTIC)), { _, _ -> flowOf(emptyList()) }, 1_000).first()
        }
        assertFalse(snapshot.modelFailed)
    }

    @Test fun modelErrorBelongsToCurrentSemanticRequest() = runBlocking {
        val request = SearchRequest("cat", SearchMode.SEMANTIC)
        val snapshot = homeSearchSnapshots(flowOf(request), { _, _ -> flow { throw IllegalStateException("model unavailable") } }, 0).first()
        assertEquals(request, snapshot.request)
        assertTrue(snapshot.modelFailed)
        assertTrue(snapshot.results.isEmpty())
    }
}
