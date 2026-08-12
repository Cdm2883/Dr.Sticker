package vip.cdms.drsticker.services.shizuku

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import androidx.annotation.Keep
import org.lsposed.hiddenapibypass.HiddenApiBypass

@Keep
class ShizukuUserService : IShizukuUserService.Stub() {
    init {
        try {
            @SuppressLint("NewApi")
            HiddenApiBypass.addHiddenApiExemptions("L")
        } catch (_: Throwable) {
        }
    }

    private val inputInjector = ShizukuInputManager()
    private val taskObserver = ShizukuActivityListener()

    override fun registerConditionListener(callback: IBinder?) =
        taskObserver.register(callback)

    override fun unregisterConditionListener() =
        taskObserver.unregister()

    override fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long,
    ) = inputInjector.swipe(startX, startY, endX, endY, durationMillis)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == SHIZUKU_DESTROY_TRANSACTION) {
            taskObserver.unregister()
            reply?.writeNoException()
            Thread {
                Thread.sleep(100)
                Process.killProcess(Process.myPid())
            }.apply { isDaemon = true }.start()
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    private companion object {
        const val SHIZUKU_DESTROY_TRANSACTION = 16777115
    }
}
