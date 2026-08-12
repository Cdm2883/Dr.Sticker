package vip.cdms.drsticker.services.shizuku

import android.annotation.SuppressLint
import android.hardware.input.IInputManager
import android.os.*
import android.util.Log
import android.view.Display
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.io.FileDescriptor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class ShizukuInputManager {
    private val iInputManager: IInputManager by lazy {
        @SuppressLint("PrivateApi")
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "input") as IBinder
        IInputManager.Stub.asInterface(binder)
    }

    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long,
    ): Boolean {
        val x1 = startX.toFloat()
        val y1 = startY.toFloat()
        val x2 = endX.toFloat()
        val y2 = endY.toFloat()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runShellCommand(
                "swipe", x1.toString(), y1.toString(), x2.toString(), y2.toString(), durationMillis.toString(),
            )
        } else {
            legacySwipe(x1, y1, x2, y2, durationMillis)
        }
    }


    // https://github.com/gkd-kit/gkd/blob/main/app/src/main/kotlin/li/songe/gkd/priv/BinderExt.kt
    private fun runShellCommand(vararg args: String): Boolean {
        val stdinPipe = ParcelFileDescriptor.createPipe()
        val stdoutPipe = ParcelFileDescriptor.createPipe()
        val stderrPipe = ParcelFileDescriptor.createPipe()
        val stdout = stdoutPipe[0].readTextAsync()
        val stderr = stderrPipe[0].readTextAsync()
        val resultLatch = CountDownLatch(1)
        var shellResultCode = -1
        val resultReceiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                shellResultCode = resultCode
                resultLatch.countDown()
            }
        }

        try {
            stdinPipe[1].closeQuietly()
            invokeShellCommand(
                input = stdinPipe[0].fileDescriptor,
                out = stdoutPipe[1].fileDescriptor,
                err = stderrPipe[1].fileDescriptor,
                args = args,
                resultReceiver = resultReceiver,
            )
            if (!resultLatch.await(SHELL_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "input ${args.joinToString(" ")} timed out.")
                return false
            }
        } catch (cause: Throwable) {
            Log.w(TAG, "input ${args.joinToString(" ")} threw.", cause)
            return false
        } finally {
            stdinPipe[0].closeQuietly()
            stdoutPipe[1].closeQuietly()
            stderrPipe[1].closeQuietly()
        }

        /* val result = */ stdout.getTextOrEmpty()
        val error = stderr.getTextOrEmpty()
        if (shellResultCode != 0) Log.w(
            TAG,
            "input ${args.joinToString(" ")} failed with code $shellResultCode: $error",
        )
        return shellResultCode == 0
    }

    @SuppressLint("PrivateApi")
    private fun invokeShellCommand(
        input: FileDescriptor,
        out: FileDescriptor,
        err: FileDescriptor,
        args: Array<out String>,
        resultReceiver: ResultReceiver,
    ) = IBinder::class.java.getMethod(
        "shellCommand",
        FileDescriptor::class.java,
        FileDescriptor::class.java,
        FileDescriptor::class.java,
        Array<String>::class.java,
        Class.forName("android.os.ShellCallback"),
        ResultReceiver::class.java,
    ).invoke(
        iInputManager.asBinder(),
        input,
        out,
        err,
        args,
        /* shellCallback */ null,
        resultReceiver
    )

    private fun ParcelFileDescriptor.readTextAsync(
        threadName: String = "IBinder.shellCommand",
    ): FutureTask<String> {
        val task = FutureTask {
            ParcelFileDescriptor.AutoCloseInputStream(this)
                .bufferedReader().use { it.readText() }
        }
        Thread(task, threadName).apply {
            isDaemon = true
            start()
        }
        return task
    }

    private fun FutureTask<String>.getTextOrEmpty() =
        runCatching { get() }.getOrDefault("")

    private fun ParcelFileDescriptor.closeQuietly() =
        runCatching { close() }


    // https://github.com/gkd-kit/gkd/blob/main/app/src/main/kotlin/li/songe/gkd/priv/CompatInputManager.kt
    private fun legacySwipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMillis: Long,
    ): Boolean {
        val safeDuration = if (durationMillis < 0) 300L else durationMillis
        val downTime = SystemClock.uptimeMillis()
        var success = injectMotionEvent(
            inputSource = InputDevice.SOURCE_TOUCHSCREEN,
            action = MotionEvent.ACTION_DOWN,
            downTime = downTime,
            eventTime = downTime,
            x = x1,
            y = y1,
            pressure = 1.0f,
        )
        var now = SystemClock.uptimeMillis()
        val endTime = downTime + safeDuration
        while (now < endTime) {
            val elapsedTime = now - downTime
            val alpha = elapsedTime.toFloat() / safeDuration
            success = injectMotionEvent(
                inputSource = InputDevice.SOURCE_TOUCHSCREEN,
                action = MotionEvent.ACTION_MOVE,
                downTime = downTime,
                eventTime = now,
                x = lerp(x1, x2, alpha),
                y = lerp(y1, y2, alpha),
                pressure = 1.0f,
            ) && success
            now = SystemClock.uptimeMillis()
        }
        return injectMotionEvent(
            inputSource = InputDevice.SOURCE_TOUCHSCREEN,
            action = MotionEvent.ACTION_UP,
            downTime = downTime,
            eventTime = now,
            x = x2,
            y = y2,
            pressure = 0.0f,
        ) && success
    }

    @Suppress("SameParameterValue")
    private fun injectMotionEvent(
        inputSource: Int,
        action: Int,
        downTime: Long,
        eventTime: Long,
        x: Float,
        y: Float,
        pressure: Float,
    ): Boolean {
        val event = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            x,
            y,
            pressure,
            DEFAULT_SIZE,
            DEFAULT_META_STATE,
            DEFAULT_PRECISION_X,
            DEFAULT_PRECISION_Y,
            getInputDeviceId(inputSource),
            DEFAULT_EDGE_FLAGS,
        )
        return try {
            event.source = inputSource
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val displayId = if ((inputSource and InputDevice.SOURCE_CLASS_POINTER) != 0) {
                    Display.DEFAULT_DISPLAY
                } else {
                    Display.INVALID_DISPLAY
                }
                runCatching {
                    MotionEvent::class.java
                        .getMethod("setDisplayId", Int::class.java)
                        .invoke(event, displayId)
                }
            }
            injectInputEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun injectInputEvent(event: InputEvent) = try {
        iInputManager.injectInputEvent(event, INJECT_MODE_WAIT_FOR_FINISH)
    } catch (cause: Throwable) {
        Log.w(TAG, "Failed to inject input event.", cause)
        false
    }

    private fun getInputDeviceId(inputSource: Int): Int {
        for (deviceId in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            if (device.supportsSource(inputSource)) return deviceId
        }
        return DEFAULT_DEVICE_ID
    }

    private fun lerp(a: Float, b: Float, alpha: Float) =
        (b - a) * alpha + a

    private companion object {
        const val TAG = "ShizukuInputManager"
        const val DEFAULT_SIZE = 1.0f
        const val DEFAULT_META_STATE = 0
        const val DEFAULT_PRECISION_X = 1.0f
        const val DEFAULT_PRECISION_Y = 1.0f
        const val DEFAULT_DEVICE_ID = 0
        const val DEFAULT_EDGE_FLAGS = 0

        // InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
        const val INJECT_MODE_WAIT_FOR_FINISH = 2
        const val SHELL_COMMAND_TIMEOUT_MILLIS = 10_000L
    }
}
