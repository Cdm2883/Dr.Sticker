package vip.cdms.drsticker.data

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import vip.cdms.drsticker.data.repositories.StickerRepository
import javax.inject.Inject

data class SourceStickerRequest(
    val resource: SourceStickerResource,
    val setId: StickerSetId,
    val stickerId: StickerId?,
)

class StickerSourceFetcher(
    private val request: SourceStickerRequest,
    private val stickerRepository: StickerRepository,
) : Fetcher {
    override suspend fun fetch(): SourceResult = withContext(Dispatchers.IO) {
        val file = stickerRepository
            .fetchStickerResource(request.setId, request.stickerId, request.resource)
        SourceResult(
            source = ImageSource(file = file.absolutePath.toPath()),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }
}

class StickerSourceFetcherFactory @Inject constructor(
    private val stickerRepository: StickerRepository,
) : Fetcher.Factory<SourceStickerRequest> {
    override fun create(data: SourceStickerRequest, options: Options, imageLoader: ImageLoader) =
        StickerSourceFetcher(data, stickerRepository)
}
