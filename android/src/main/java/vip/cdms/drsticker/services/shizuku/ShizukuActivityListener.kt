package vip.cdms.drsticker.services.shizuku

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.IActivityManager
import android.app.ITaskStackListener
import android.content.ComponentName
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import android.util.Log

class ShizukuActivityListener {
    private val iActivityManager: IActivityManager by lazy {
        @SuppressLint("PrivateApi")
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "activity") as IBinder
        IActivityManager.Stub.asInterface(binder)
    }

    private var callback: IShizukuConditionCallback? = null
    private var registered = false

    private val taskStackListener = object : ITaskStackListener.Stub() {
        // https://github.com/gkd-kit/gkd/issues/941#issuecomment-2784035441
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = try {
            super.onTransact(code, data, reply, flags)
        } catch (_: Throwable) {
            true
        }

        override fun onTaskStackChanged() = emitTopTask()

        override fun onTaskMovedToFront(taskId: Int) = emitTopTask()

        override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) = emitTopTask()
    }

    fun register(callback: IBinder?): Boolean {
        this.callback = IShizukuConditionCallback.Stub.asInterface(callback)
        return try {
            if (!registered) {
                iActivityManager.registerTaskStackListener(taskStackListener)
                registered = true
            }
            emitTopTask()
            true
        } catch (cause: Throwable) {
            Log.w(TAG, "Failed to register TaskStackListener.", cause)
            false
        }
    }

    fun unregister() {
        callback = null
        try {
            if (registered) {
                iActivityManager.unregisterTaskStackListener(taskStackListener)
                registered = false
            }
        } catch (cause: Throwable) {
            Log.w(TAG, "Failed to unregister TaskStackListener.", cause)
        }
    }

    private fun emitTopTask() {
        val component = queryTopActivity()
        if (component == null) {
            callback?.onTopTaskChanged("", "")
        } else {
            callback?.onTopTaskChanged(component.packageName, component.className)
        }
    }

    private fun queryTopActivity(): ComponentName? = try {
        val tasks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            iActivityManager.getTasks(1)
        } else {
            iActivityManager.getTasks(1, 0)
        }
        tasks.firstOrNull()?.topActivity
    } catch (cause: Throwable) {
        Log.w(TAG, "Failed to query top task.", cause)
        null
    }

    private companion object {
        const val TAG = "ShizukuTaskObserver"
    }
}
