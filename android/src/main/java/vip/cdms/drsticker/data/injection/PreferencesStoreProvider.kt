package vip.cdms.drsticker.data.injection

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import vip.cdms.drsticker.data.utils.PreferencesStore
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesStoreProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val stores = ConcurrentHashMap<String, PreferencesStore>()

    fun get(name: String) = stores.computeIfAbsent(name) {
        PreferencesStore(context.getSharedPreferences(it, Context.MODE_PRIVATE))
    }
}
