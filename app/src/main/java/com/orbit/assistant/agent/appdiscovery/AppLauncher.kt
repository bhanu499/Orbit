package com.orbit.assistant.agent.appdiscovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AppLaunchResult {
    data class Launched(val packageName: String, val appName: String) : AppLaunchResult()
    data class AppNotInstalled(val packageName: String) : AppLaunchResult()
    data class LaunchIntentUnavailable(val packageName: String) : AppLaunchResult()
    data class CannotLaunch(val packageName: String, val reason: String) : AppLaunchResult()
}

interface AppLauncher {
    val activeExternalPackage: StateFlow<String?>
    fun launchApp(packageName: String): AppLaunchResult
    fun onUserReturnedToOrbit()
}

@Singleton
class AndroidAppLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) : AppLauncher {

    private val _activeExternalPackage = MutableStateFlow<String?>(null)
    override val activeExternalPackage: StateFlow<String?> = _activeExternalPackage.asStateFlow()

    override fun launchApp(packageName: String): AppLaunchResult {
        val pm = context.packageManager
        val isInstalled = runCatching {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            true
        }.getOrElse { false }

        if (!isInstalled) {
            return AppLaunchResult.AppNotInstalled(packageName)
        }

        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return AppLaunchResult.LaunchIntentUnavailable(packageName)

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return runCatching {
            val appName = pm.getApplicationLabel(
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            ).toString()
            context.startActivity(launchIntent)
            _activeExternalPackage.value = packageName
            AppLaunchResult.Launched(packageName, appName)
        }.getOrElse { error ->
            AppLaunchResult.CannotLaunch(packageName, error.localizedMessage ?: "Unable to start activity")
        }
    }

    override fun onUserReturnedToOrbit() {
        _activeExternalPackage.value = null
    }
}
