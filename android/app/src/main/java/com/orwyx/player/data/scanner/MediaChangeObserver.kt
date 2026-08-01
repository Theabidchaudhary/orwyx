package com.orwyx.player.data.scanner

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Keeps the library current without ever forcing a rescan on app open.
 *
 * Registers a long-lived [ContentObserver] on the video collection; any insert,
 * update, or delete anywhere on the device (this app, the camera, a file
 * manager, ADB) enqueues a signal that is debounced and coalesced into a
 * single [MediaScanner.scan] call, so a batch of changes triggers one scan
 * instead of one per file.
 */
class MediaChangeObserver(
    private val context: Context,
    private val scanner: MediaScanner,
    private val scope: CoroutineScope,
) {
    private val changeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            changeSignal.tryEmit(Unit)
        }
    }

    fun start() {
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        scope.launch {
            changeSignal.debounce(DEBOUNCE_MS).collect { scanner.scan(silent = true) }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 2_500L
    }
}
