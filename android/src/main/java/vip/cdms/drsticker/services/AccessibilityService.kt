package vip.cdms.drsticker.services

import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellableContinuation
import javax.inject.Inject
import kotlin.coroutines.resume

@SuppressLint("AccessibilityPolicy")
@AndroidEntryPoint
class AccessibilityService : android.accessibilityservice.AccessibilityService() {
    @Inject
    lateinit var bridge: AccessibilityBridge

    @Inject
    lateinit var conditionContext: ConditionContext

    override fun onServiceConnected() =
        bridge.connect(this)

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        bridge.disconnect(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (conditionContext.update(event))
            bridge.emitConditionContext(conditionContext)
    }

    internal fun updateEventDemand(enabled: Boolean) = setServiceInfo(serviceInfo.apply {
        if (enabled) conditionContext.reset()

        eventTypes = if (!enabled) 0
        else AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    })

    internal fun dispatch(
        stroke: GestureDescription.StrokeDescription,
        continuation: CancellableContinuation<AccessibilityBridge.GestureResult>,
    ) {
        val accepted = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    if (continuation.isActive) continuation.resume(AccessibilityBridge.GestureResult.Completed)
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    if (continuation.isActive) continuation.resume(AccessibilityBridge.GestureResult.Cancelled)
                }
            },
            null,
        )
        if (!accepted && continuation.isActive)
            continuation.resume(AccessibilityBridge.GestureResult.Unavailable)
    }
}
