package com.orbit.assistant.agent.appdiscovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val supportedPackages = setOf(
        "com.grofers.customerapp",       // Blinkit
        "com.zeptoconsumerapp",          // Zepto
        "in.swiggy.android",             // Swiggy
        "com.bigbasket.mobileapp",       // BigBasket
        "in.amazon.mShop.android.shopping", // Amazon
        "com.flipkart.android",          // Flipkart
        "com.google.android.calendar",   // Google Calendar
        "com.google.android.apps.maps",  // Google Maps
        "com.whatsapp"                   // WhatsApp
    )

    /**
     * Queries only user-facing launcher activities declared under <queries> in AndroidManifest.xml.
     * Does not indiscriminately query all installed packages.
     */
    suspend fun discoverLaunchableApps(): List<AppDescriptor> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = runCatching {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        }.getOrElse { emptyList() }

        resolveInfos
            .asSequence()
            .filter { it.activityInfo?.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val appName = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                val icon = runCatching { info.loadIcon(pm) }.getOrNull()
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                AppDescriptor(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    launchIntent = launchIntent,
                    isSupported = pkg in supportedPackages
                )
            }
            .sortedWith(compareByDescending<AppDescriptor> { it.isSupported }.thenBy { it.appName })
            .toList()
    }

    suspend fun findByPackage(packageName: String): AppDescriptor? {
        return discoverLaunchableApps().firstOrNull { it.packageName == packageName }
    }
}
