package vip.cdms.drsticker.services

import android.app.Service
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import vip.cdms.drsticker.data.SourceStickerResource
import vip.cdms.drsticker.data.StickerId
import vip.cdms.drsticker.data.StickerSetId
import vip.cdms.drsticker.data.repositories.RulesetRepository
import vip.cdms.drsticker.data.repositories.StatisticRepository
import vip.cdms.drsticker.data.repositories.StickerRepository
import vip.cdms.drsticker.rule.Ruleset
import vip.cdms.drsticker.rule.preprocess.PreprocessCacheKey
import vip.cdms.drsticker.rule.preprocess.ProcessingSticker
import vip.cdms.drsticker.rule.triggers.TriggerSession
import vip.cdms.drsticker.services.picker.StickerPickerSheetController
import vip.cdms.drsticker.services.shizuku.ShizukuBridge
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class StickerService : Service() {
    @Inject
    lateinit var serviceController: StickerServiceController

    @Inject
    lateinit var notificationFactory: StickerNotificationFactory

    @Inject
    lateinit var accessibilityBridge: AccessibilityBridge

    @Inject
    lateinit var shizukuBridge: ShizukuBridge

    @Inject
    lateinit var rulesetRepository: RulesetRepository

    @Inject
    lateinit var stickerPickerSheetController: StickerPickerSheetController

    @Inject
    lateinit var stickerRepository: StickerRepository

    @Inject
    lateinit var statisticRepository: StatisticRepository

    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var rulesets: List<Ruleset> = emptyList()
    private var activeRuleset: Ruleset? = null
    private var activeTrigger: TriggerSession? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceController.isSettingsEnabled = false  // edit back from notification
            serviceController.reportStopping()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RESTART) {
            if (!started) return START_STICKY
            return try {
                refresh()
                START_STICKY
            } catch (cause: Throwable) {
                fail("Failed to restart sticker service.", cause)
                START_NOT_STICKY
            }
        }
        if (!serviceController.isSettingsEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (started) return START_STICKY
        serviceController.reportStarting()
        return try {
            startForeground(
                StickerNotificationFactory.NOTIFICATION_ID,
                notificationFactory.create(),
            )
            refresh()
            started = true
            serviceController.reportRunning()
            START_STICKY
        } catch (cause: Throwable) {
            fail("Failed to start sticker service.", cause)
            START_NOT_STICKY
        }
    }

    fun refresh() {
        rulesets = rulesetRepository.getRulesetIndexes()
            .asSequence()
            .filter { it.isEnabled }
            .map { rulesetRepository.getRuleset(it.rulesetId) }
            .toList()
        activeTrigger?.close()
        activeTrigger = null
        activeRuleset = null
        stickerPickerSheetController.hide()
        val consumer = if (rulesets.isEmpty()) null else ::matchRuleset
        val useShizuku = shizukuBridge.isAvailable()
        shizukuBridge.setConditionConsumer(if (useShizuku) consumer else null)
        accessibilityBridge.setConditionConsumer(if (useShizuku) null else consumer)
    }

    override fun onDestroy() {
        started = false
        accessibilityBridge.setConditionConsumer(null)
        shizukuBridge.setConditionConsumer(null)
        activeTrigger?.close()
        activeTrigger = null
        activeRuleset = null
        rulesets = emptyList()
        stickerPickerSheetController.close()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceController.reportStopped()
        super.onDestroy()
    }

    private fun matchRuleset(context: ConditionContext) {
        val matched = rulesets.firstOrNull { it.condition.matches(context) }
        if (matched?.rulesetId == activeRuleset?.rulesetId) return

        activeTrigger?.close()
        activeTrigger = null
        activeRuleset = null
        stickerPickerSheetController.hide()
        if (matched == null) return

        try {
            val trigger = rulesetRepository
                .getTriggerHandler(matched.trigger)
                .activate(matched.trigger, ::openPicker)
            activeRuleset = matched
            activeTrigger = trigger
        } catch (cause: Throwable) {
            fail("Failed to activate ruleset '${matched.rulesetId}'.", cause)
        }
    }

    private fun openPicker() = stickerPickerSheetController.show { setId, stickerId, resource ->
        stickerPickerSheetController.hide()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sendSticker(setId, stickerId, resource)
        }
    }

    private suspend fun sendSticker(
        setId: StickerSetId,
        stickerId: StickerId,
        resource: SourceStickerResource,
    ) = try {
        val ruleset = activeRuleset ?: return
        val original = withContext(Dispatchers.IO)
        { stickerRepository.fetchStickerResource(setId, stickerId, resource) }
        val processed = preprocessSticker(
            setId, stickerId, ruleset,
            original, resource.getRealExtension()
        )
        rulesetRepository
            .getAdapterHandler(ruleset.adapter)
            .send(ruleset.adapter, processed)
        withContext(Dispatchers.IO) { statisticRepository.trackStickerUsage(setId, stickerId) }
        Log.i(TAG, "Sticker handoff completed.")
        Unit
    } catch (cause: Throwable) {
        Log.e(TAG, "Failed to hand off sticker '$stickerId' from set '$setId'.", cause)
        Unit
    }

    private suspend fun preprocessSticker(
        setId: StickerSetId,
        stickerId: StickerId,
        ruleset: Ruleset,
        original: File,
        originalExtension: String,
    ): File {
        if (ruleset.preprocesses.isEmpty()) return original

        val cacheKey = PreprocessCacheKey(setId, stickerId, ruleset.preprocesses)
        withContext(Dispatchers.IO)
        { rulesetRepository.getPreprocessCache(cacheKey) }?.let { return it }

        val initial = ProcessingSticker(original.readBytes(), originalExtension)
        var processed = initial
        for (config in ruleset.preprocesses) {
            val output = rulesetRepository
                .getPreprocessHandler(config)
                .process(config, processed)
            if (output == null) {
                if (processed === initial) return original
                break
            }
            processed = output
        }
        return withContext(Dispatchers.IO) {
            rulesetRepository.updatePreprocessCache(cacheKey, processed)
        }
    }

    private fun fail(message: String, cause: Throwable) {
        Log.e(TAG, message, cause)
        val effectiveCause = runCatching {
            serviceController.isSettingsEnabled = false
        }.exceptionOrNull()?.let { persistenceCause ->
            cause.apply { addSuppressed(persistenceCause) }
        } ?: cause
        serviceController.reportFailure(effectiveCause)
        stopSelf()
    }

    companion object {
        private const val TAG = "StickerService"
        const val ACTION_START = "vip.cdms.drsticker.action.START"
        const val ACTION_STOP = "vip.cdms.drsticker.action.STOP"
        const val ACTION_RESTART = "vip.cdms.drsticker.action.RESTART"
    }
}
