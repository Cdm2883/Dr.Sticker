package vip.cdms.drsticker.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.SliderPreference

@Composable
fun SliderPreference(
    value: Float,
    onValueChange: (Float) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    valueSteps: Int = 0,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable (() -> Unit)? = null,
    valueText: @Composable ((Float) -> Unit)? = null,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    SliderPreference(
        value = value,
        onValueChange = onValueChange, // 松手触发保存
        sliderValue = sliderValue,
        onSliderValueChange = { sliderValue = it }, // 拖动中仅更新 UI
        title = title,
        modifier = modifier,
        valueRange = valueRange,
        valueSteps = valueSteps,
        enabled = enabled,
        icon = icon,
        summary = summary,
        valueText = valueText?.let { { it(sliderValue) } },
    )
}

@Composable
fun <T> ListPreference(
    value: T,
    onValueChange: (T) -> Unit,
    values: List<T>,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (() -> Unit)? = null,
    summary: @Composable ((AnnotatedString) -> Unit)? = null,
    type: ListPreferenceType = ListPreferenceType.ALERT_DIALOG,
    valueToText: @Composable (T) -> AnnotatedString = { AnnotatedString(it.toString()) },
) {
    ListPreference(
        value = value,
        onValueChange = onValueChange,
        values = values,
        title = title,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        summary = summary?.let { { summary(valueToText(value)) } },
        type = type,
        valueToText = valueToText,
    )
}
