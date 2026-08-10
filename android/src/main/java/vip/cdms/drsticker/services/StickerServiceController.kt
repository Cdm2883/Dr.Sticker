package vip.cdms.drsticker.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface StickerServiceState {
    object Stopped : StickerServiceState
    object Starting : StickerServiceState
    object Running : StickerServiceState
    object Stopping : StickerServiceState
    class Failed(val cause: Throwable) : StickerServiceState
}

@Singleton
class StickerServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val PREFERENCES_NAME = "sticker_service"
        const val KEY_ENABLED = "enabled"
    }

    private val preferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var isSettingsEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        @SuppressLint("UseKtx")
        set(value) = check(
            preferences.edit()
                .putBoolean(KEY_ENABLED, value)
                .commit()
        ) {
            "Failed to persist sticker service enabled state."
        }

    private val _state = MutableStateFlow<StickerServiceState>(StickerServiceState.Stopped)
    val state = _state.asStateFlow()

    @Synchronized
    fun start() {
        if (_state.value !is StickerServiceState.Stopped
            && _state.value !is StickerServiceState.Failed
        ) return

        _state.value = StickerServiceState.Starting
        try {
            isSettingsEnabled = true
            val intent = Intent(context, StickerService::class.java)
                .setAction(StickerService.ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        } catch (cause: Throwable) {
            runCatching { isSettingsEnabled = false }
            _state.value = StickerServiceState.Failed(cause)
        }
    }

    @Synchronized
    fun stop() {
        if (_state.value !is StickerServiceState.Running) return

        try {
            isSettingsEnabled = false
            _state.value = StickerServiceState.Stopping
            val intent = Intent(context, StickerService::class.java)
                .setAction(StickerService.ACTION_STOP)
            context.startService(intent)
        } catch (cause: Throwable) {
            _state.value = StickerServiceState.Failed(cause)
        }
    }


    internal fun reportStarting() {
        _state.value = StickerServiceState.Starting
    }

    internal fun reportRunning() {
        _state.value = StickerServiceState.Running
    }

    internal fun reportStopping() {
        if (_state.value !is StickerServiceState.Failed) {
            _state.value = StickerServiceState.Stopping
        }
    }

    internal fun reportFailure(cause: Throwable) {
        _state.value = StickerServiceState.Failed(cause)
    }

    internal fun reportStopped() {
        if (_state.value !is StickerServiceState.Failed) {
            _state.value = StickerServiceState.Stopped
        }
    }
}
