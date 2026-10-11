package com.darkesttrololo.memeizer.ui.home

import com.darkesttrololo.memeizer.data.db.ClipProgress
import com.darkesttrololo.memeizer.data.search.SearchMode
import com.darkesttrololo.memeizer.data.search.SearchResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SemanticStateAndRetryTest {
    private val base = HomeUiState(mode = SearchMode.SEMANTIC, query = "cat", content = HomeContentState.NoResults)

    @Test fun completedFailuresAreDifferentFromPendingAndEmpty() {
        assertEquals(HomeContentState.SemanticIndexFailed, semanticContentState(base, ClipProgress(4, 0, 4), false))
        assertEquals(HomeContentState.SemanticIndexNotReady, semanticContentState(base, ClipProgress(4, 0, 3), false))
        assertEquals(HomeContentState.SemanticIndexNotReady, semanticContentState(base, ClipProgress(4), false))
        val empty = base.copy(content = HomeContentState.EmptyFolders)
        assertEquals(HomeContentState.EmptyFolders, semanticContentState(empty, ClipProgress(), false))
    }

    @Test fun partialFailureRetainsResultsAndModelErrorRemainsSeparate() {
        val image = SearchResult(1, "content://test", "synthetic.png", "", null, null, null)
        val images = base.copy(results = listOf(image), content = HomeContentState.Images)
        assertEquals(HomeContentState.Images, semanticContentState(images, ClipProgress(4, 1, 3), false))
        assertEquals(HomeContentState.Images, semanticContentState(images, ClipProgress(4, 1, 3), true))
        assertEquals(HomeContentState.ModelError, semanticContentState(base, ClipProgress(4), true))
        assertEquals(HomeContentState.NoResults, semanticContentState(base, ClipProgress(4, 1, 3), false))
    }

    @Test fun blankQueryKeepsGalleryAndTextModeIsUnaffected() {
        val gallery = base.copy(query = "", content = HomeContentState.Images)
        assertEquals(HomeContentState.Images, semanticContentState(gallery, ClipProgress(4, 0, 4), false))
        assertEquals(HomeContentState.NoResults, semanticContentState(base.copy(mode = SearchMode.TEXT), ClipProgress(4, 0, 4), true))
    }

    @Test fun duplicateTapsWaitForAcceptanceAndQueuedWorkBlocksRetry() = runBlocking {
        val accepted = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var queued = false
        var calls = 0
        val retry = SemanticRetry({ queued }) {
            calls++
            entered.complete(Unit)
            accepted.await()
            queued = true
        }
        val submission = launch { retry.retry() }
        entered.await()
        assertTrue(retry.state.value.submitting)
        retry.retry()
        assertEquals(1, calls)
        accepted.complete(Unit)
        submission.join()
        assertFalse(retry.state.value.submitting)
        retry.retry()
        assertEquals(1, calls)
    }

    @Test fun schedulingFailureAllowsAnotherAttempt() = runBlocking {
        var calls = 0
        val retry = SemanticRetry({ false }) { if (++calls == 1) error("synthetic scheduling failure") }
        retry.retry()
        assertTrue(retry.state.value.schedulingFailed)
        assertFalse(retry.state.value.submitting)
        retry.retry()
        assertEquals(2, calls)
        assertFalse(retry.state.value.schedulingFailed)
    }

    @Test fun cancellationIsPropagatedAndReleasesSubmissionGuard() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val retry = SemanticRetry({ false }) { started.complete(Unit); awaitCancellation() }
        val submission = launch { retry.retry() }
        started.await()
        submission.cancelAndJoin()
        assertFalse(retry.state.value.submitting)
        assertFalse(retry.state.value.schedulingFailed)
    }
}
