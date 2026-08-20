package chat.stoat

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import android.webkit.WebView
import chat.stoat.di.appModule
import chat.stoat.di.viewModelModule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.google.android.material.color.DynamicColors
import io.livekit.android.LiveKit
import io.livekit.android.util.LoggingLevel
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.logcat
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class StoatApplication : Application(), SingletonImageLoader.Factory {
    companion object {
        lateinit var instance: StoatApplication

        private const val INSTALL_STATE_PREFS =
            "homelab_install_state"
        private const val LAST_VERSION_CODE =
            "last_version_code"
    }

    override fun onCreate() {
        super.onCreate()
        clearCachesAfterUpgrade()
        AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)

        if (BuildConfig.DEBUG) {
            LiveKit.loggingLevel = LoggingLevel.DEBUG
        }

        startKoin {
            androidContext(this@StoatApplication)
            androidLogger()
            modules(appModule, viewModelModule)
        }

        if (BuildConfig.DEBUG) {
            // Enable strict mode primarily to catch non-API usage, although we detect all
            // violations for our reference.
            // https://developer.android.com/reference/android/os/StrictMode
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy
                    .Builder()
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            detectNonSdkApiUsage()
                        }
                        penaltyLog()
                    }
                    .build()
            )
        }
    }


private fun clearCachesAfterUpgrade() {
    val prefs = getSharedPreferences(
        INSTALL_STATE_PREFS,
        MODE_PRIVATE
    )

    val previousVersionCode =
        prefs.getLong(LAST_VERSION_CODE, -1L)

    val currentVersionCode =
        BuildConfig.VERSION_CODE.toLong()

    if (
        previousVersionCode >= 0L &&
        currentVersionCode > previousVersionCode
    ) {
        logcat {
            "APK upgraded from versionCode=" +
                "$previousVersionCode to " +
                "$currentVersionCode; clearing stale caches"
        }

        runCatching {
            cacheDir.listFiles()?.forEach {
                it.deleteRecursively()
            }
        }.onFailure {
            logcat(LogPriority.WARN) {
                "Could not clear app cache: ${it.message}"
            }
        }

        runCatching {
            codeCacheDir.listFiles()?.forEach {
                it.deleteRecursively()
            }
        }.onFailure {
            logcat(LogPriority.WARN) {
                "Could not clear code cache: ${it.message}"
            }
        }

        runCatching {
            externalCacheDir?.listFiles()?.forEach {
                it.deleteRecursively()
            }
        }.onFailure {
            logcat(LogPriority.WARN) {
                "Could not clear external cache: ${it.message}"
            }
        }

        runCatching {
            WebView(this).apply {
                clearCache(true)
                clearHistory()
                destroy()
            }
        }.onFailure {
            logcat(LogPriority.WARN) {
                "Could not clear WebView cache: ${it.message}"
            }
        }
    }

    prefs.edit()
        .putLong(
            LAST_VERSION_CODE,
            currentVersionCode
        )
        .apply()
}

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .crossfade(true)
            .build()
    }

    init {
        instance = this
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
