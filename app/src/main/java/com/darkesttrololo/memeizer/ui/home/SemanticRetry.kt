package com.darkesttrololo.memeizer.ui.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SemanticRetryState(val submitting: Boolean = false, val schedulingFailed: Boolean = false)

internal class SemanticRetry(
    private val workIsLoading: suspend () -> Boolean,
    private val submit: suspend () -> Unit,
) {
    private val mutableState = MutableStateFlow(SemanticRetryState())
    val state = mutableState.asStateFlow()

    suspend fun retry() {
        val before = mutableState.value
        if (before.submitting || !mutableState.compareAndSet(before, before.copy(submitting = true))) return
        var failed = before.schedulingFailed
        try {
            if (workIsLoading()) return
            submit()
            failed = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            mutableState.value = SemanticRetryState(schedulingFailed = failed)
        }
    }
}
