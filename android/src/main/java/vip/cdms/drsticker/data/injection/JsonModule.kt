package vip.cdms.drsticker.data.injection

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import vip.cdms.drsticker.data.StickerSourceConfig
import vip.cdms.drsticker.data.StickerSourceConfigSerializer
import vip.cdms.drsticker.data.StickerSourceMetadata
import vip.cdms.drsticker.data.repositories.StickerRepository
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.reflect.KClass

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StickerConfigJson

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RulesetConfigJson

@Module
@InstallIn(SingletonComponent::class)
object JsonModule {
    @Provides
    @Singleton
    fun provideNormalJson() = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    @StickerConfigJson
    fun provideStickerConfigJson(
        normalJson: Json,
        metadataMap: Map<String, @JvmSuppressWildcards StickerSourceMetadata<*>>,
        stickerRepositoryProvider: Provider<StickerRepository>,
    ) = Json(normalJson) {
        val configNamingStrategy = JsonNamingStrategy.SnakeCase

        namingStrategy = configNamingStrategy
        prettyPrint = true
        serializersModule = SerializersModule {
            polymorphic(StickerSourceConfig::class) {
                metadataMap.forEach { (sourceKey, metadata) ->
                    @Suppress("UNCHECKED_CAST")
                    val kClass = metadata.configClass as KClass<StickerSourceConfig>

                    @Suppress("UNCHECKED_CAST")
                    val delegateSerializer = metadata.configSerializer as KSerializer<StickerSourceConfig>
                    val serializer = StickerSourceConfigSerializer(
                        stickerRepositoryProvider = stickerRepositoryProvider,
                        sourceKey = sourceKey,
                        delegateSerializer = delegateSerializer,
                        namingStrategy = configNamingStrategy,
                    )
                    subclass(
                        subclass = kClass,
                        serializer = serializer
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    @RulesetConfigJson
    fun provideRulesetConfigJson(
        normalJson: Json,
    ) = Json(normalJson) {
        namingStrategy = JsonNamingStrategy.SnakeCase
        prettyPrint = true
        encodeDefaults = true
    }
}
