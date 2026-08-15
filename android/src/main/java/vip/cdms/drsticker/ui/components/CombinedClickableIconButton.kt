package vip.cdms.drsticker.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CombinedClickableIconButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit)
) = Box(
    modifier = Modifier
        .minimumInteractiveComponentSize()
        .size(IconButtonDefaults.smallContainerSize())
        .clip(IconButtonDefaults.standardShape)
        .then(modifier)
        .combinedClickable(
            onLongClick = onLongClick,
            onClick = onClick,
        ),
    contentAlignment = Alignment.Center,
    content = content
)

