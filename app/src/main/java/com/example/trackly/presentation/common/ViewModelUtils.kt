package com.example.trackly.presentation.common

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Launches [block] in the ViewModel scope; unexpected failures are logged and reported, never crash. */
fun ViewModel.launchSafely(
    onError: suspend (Exception) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit,
): Job = viewModelScope.launch {
    try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.e("ViewModel", "Action failed", error)
        onError(error)
    }
}
