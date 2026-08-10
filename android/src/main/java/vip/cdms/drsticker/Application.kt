package vip.cdms.drsticker

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class Application : android.app.Application(),
    ImageLoaderFactory {
    @Inject
    lateinit var imageLoader: ImageLoader

    companion object {
        init {
            @OptIn(ExperimentalFoundationApi::class)
            ComposeFoundationFlags.isPausableCompositionInPrefetchEnabled = false
        }
    }

    override fun newImageLoader() = imageLoader
}
