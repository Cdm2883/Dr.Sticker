package vip.cdms.drsticker.data.injection

import android.content.Context
import coil.ImageLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import vip.cdms.drsticker.data.StickerSourceFetcherFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient() = OkHttpClient.Builder()
        .build()

    @Provides
    @Singleton
    fun provideCoilImageLoader(
        @ApplicationContext context: Context,
        httpClient: OkHttpClient,
        stickerSourceFetcherFactory: StickerSourceFetcherFactory
    ) = ImageLoader.Builder(context)
        .callFactory(httpClient)
        .components { add(stickerSourceFetcherFactory) }
        .build()
}
