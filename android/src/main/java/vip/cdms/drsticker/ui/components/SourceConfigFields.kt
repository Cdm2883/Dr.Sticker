package vip.cdms.drsticker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import vip.cdms.drsticker.data.ConfigState
import vip.cdms.drsticker.data.EnvConfigState

@Composable
@JvmName("AutoSourceStringConfigField")
fun AutoSourceConfigField(
    state: ConfigState<String>,
    modifier: Modifier = Modifier,
    label: String,
    supportingText: String? = null,
    secret: Boolean = false,
    singleLine: Boolean = true,
) = EnvironmentalFieldWrapper(
    state = state,
    modifier = modifier,
) {
    SourceConfigTextField(
        value = state.value,
        onValueChange = state::update,
        modifier = Modifier.fillMaxWidth(),
        label = label,
        supportingText = supportingText,
        secret = secret,
        singleLine = singleLine,
        isError = state.showError,
    )
}

//@Composable
//@JvmName("AutoSourceBooleanConfigField")
//fun AutoSourceConfigField(
//    state: ConfigState<Boolean>,
//    label: String,
//    modifier: Modifier = Modifier,
//) = EnvironmentalFieldWrapper(
//    state = state,
//    modifier = modifier,
//) {
//    Row(
//        modifier = modifier.fillMaxWidth(),
//        verticalAlignment = Alignment.CenterVertically,
//    ) {
//        Switch(
//            checked = state.value,
//            onCheckedChange = state::update,
//        )
//        Text(text = label)
//    }
//}

@Composable
private fun SourceConfigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    label: String,
    supportingText: String?,
    secret: Boolean,
    singleLine: Boolean,
    isError: Boolean,
) {
    var secretVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        trailingIcon = if (!secret) null else {
            {
                IconButton(
                    onClick = { secretVisible = !secretVisible },
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Icon(
                        imageVector = if (secretVisible) {
                            Icons.Rounded.VisibilityOff
                        } else {
                            Icons.Rounded.Visibility
                        },
                        contentDescription = null,
                    )
                }
            }
        },
        supportingText = supportingText?.let { text -> { Text(text) } },
        isError = isError,
        visualTransformation =
            if (secret && !secretVisible) PasswordVisualTransformation()
            else VisualTransformation.None,
        singleLine = singleLine,
    )
}

@Composable
private fun EnvironmentalFieldWrapper(
    state: ConfigState<*>,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = Column(modifier = modifier.fillMaxWidth()) {
    content()
    val environmentState = state as? EnvConfigState<*>
    if (environmentState != null) Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Checkbox(
            checked = environmentState.usesEnvironment,
            onCheckedChange = environmentState::updateUsesEnvironment,
        )
        Text(
            text = "Use environment value",
            modifier = Modifier.width(240.dp),
        )
    }
}
