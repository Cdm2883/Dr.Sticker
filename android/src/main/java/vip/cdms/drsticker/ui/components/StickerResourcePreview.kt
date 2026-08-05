package vip.cdms.drsticker.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import vip.cdms.drsticker.data.SourceStickerRequest
import vip.cdms.drsticker.data.SourceStickerResource
import vip.cdms.drsticker.data.StickerId
import vip.cdms.drsticker.data.StickerSetId

@Composable
fun StickerResourcePreview(
    resource: SourceStickerResource?,
    setId: StickerSetId,
    stickerId: StickerId?,
    modifier: Modifier = Modifier,
) = when {
    resource == null -> Spacer(modifier)
    resource.extension.endsWith("tgs") -> TODO()
    resource.extension.endsWith("webm") -> TODO()
    else -> AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(SourceStickerRequest(resource, setId, stickerId))
            .diskCachePolicy(CachePolicy.DISABLED)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
