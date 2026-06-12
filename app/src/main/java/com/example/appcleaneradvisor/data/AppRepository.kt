package com.example.appcleaneradvisor.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppRepository(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager
    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(UsageStatsManager::class.java)

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        // PACKAGE_USAGE_STATS is controlled by AppOps, not by a normal runtime permission dialog.
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun loadInstalledApps(): List<AppUsageInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val usage7 = aggregateUsage(now - TimeUnit.DAYS.toMillis(7), now)
        val usage30 = aggregateUsage(now - TimeUnit.DAYS.toMillis(30), now)
        val usage90 = aggregateUsage(now - TimeUnit.DAYS.toMillis(90), now)
        val usage365 = aggregateUsage(now - TimeUnit.DAYS.toMillis(365), now)

        val launcherPackages = loadLauncherPackages()
        val keyboardPackages = loadKeyboardPackages()
        val apps = loadApplicationInfos()

        apps.mapNotNull { applicationInfo ->
            val packageName = applicationInfo.packageName
            val packageInfo = packageInfo(packageName) ?: return@mapNotNull null
            val appName = applicationInfo.loadLabel(packageManager).toString()
            val isSystemApp = applicationInfo.isSystemApp()
            val criticalReason = detectCriticalReason(
                appName = appName,
                packageName = packageName,
                isSystemApp = isSystemApp,
                launcherPackages = launcherPackages,
                keyboardPackages = keyboardPackages
            )
            val sizeBytes = loadPackageSize(packageName)
            val lastTimeUsed = listOfNotNull(
                usage7[packageName]?.lastTimeUsedMillis,
                usage30[packageName]?.lastTimeUsedMillis,
                usage90[packageName]?.lastTimeUsedMillis,
                usage365[packageName]?.lastTimeUsedMillis
            ).maxOrNull()

            val result = AppRecommendationScorer.classify(
                isSystemApp = isSystemApp,
                criticalReason = criticalReason,
                lastTimeUsedMillis = lastTimeUsed,
                usage7DaysMillis = usage7[packageName]?.totalForegroundMillis ?: 0L,
                usage30DaysMillis = usage30[packageName]?.totalForegroundMillis ?: 0L,
                usage90DaysMillis = usage90[packageName]?.totalForegroundMillis ?: 0L,
                sizeBytes = sizeBytes,
                firstInstallTimeMillis = packageInfo.firstInstallTime,
                nowMillis = now
            )

            AppUsageInfo(
                appName = appName,
                packageName = packageName,
                icon = runCatching { applicationInfo.loadIcon(packageManager) }.getOrNull(),
                isSystemApp = isSystemApp,
                criticalReason = criticalReason,
                lastTimeUsedMillis = lastTimeUsed,
                usage7DaysMillis = usage7[packageName]?.totalForegroundMillis ?: 0L,
                usage30DaysMillis = usage30[packageName]?.totalForegroundMillis ?: 0L,
                usage90DaysMillis = usage90[packageName]?.totalForegroundMillis ?: 0L,
                sizeBytes = sizeBytes,
                firstInstallTimeMillis = packageInfo.firstInstallTime,
                recommendation = result.type,
                recommendationReasons = result.reasons,
                candidateScore = result.score
            )
        }.sortedBy { it.appName.lowercase(Locale.ROOT) }
    }

    private fun aggregateUsage(beginMillis: Long, endMillis: Long): Map<String, UsageAggregate> {
        val stats = runCatching {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                beginMillis,
                endMillis
            )
        }.getOrNull().orEmpty()

        val result = mutableMapOf<String, UsageAggregate>()
        // Android returns usage buckets; merge them into one record per package for each range.
        for (entry in stats) {
            val aggregate = result.getOrPut(entry.packageName) { UsageAggregate() }
            aggregate.totalForegroundMillis += entry.totalTimeInForeground
            if (entry.lastTimeUsed > aggregate.lastTimeUsedMillis) {
                aggregate.lastTimeUsedMillis = entry.lastTimeUsed
            }
        }
        return result
    }

    private fun loadApplicationInfos(): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }

    private fun packageInfo(packageName: String): PackageInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull()

    private fun loadPackageSize(packageName: String): Long? {
        val storageStatsManager = context.getSystemService(StorageStatsManager::class.java)
        return runCatching {
            val stats = storageStatsManager.queryStatsForPackage(
                StorageManager.UUID_DEFAULT,
                packageName,
                Process.myUserHandle()
            )
            stats.appBytes + stats.dataBytes + stats.cacheBytes
        }.getOrNull()
    }

    private fun loadLauncherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        val defaultHome = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
        }.getOrNull()?.activityInfo?.packageName

        return (resolved.mapNotNull { it.activityInfo?.packageName } + listOfNotNull(defaultHome)).toSet()
    }

    private fun loadKeyboardPackages(): Set<String> {
        val inputMethodManager = context.getSystemService(InputMethodManager::class.java)
        return inputMethodManager.enabledInputMethodList.mapNotNull { it.packageName }.toSet()
    }

    private fun detectCriticalReason(
        appName: String,
        packageName: String,
        isSystemApp: Boolean,
        launcherPackages: Set<String>,
        keyboardPackages: Set<String>
    ): String? {
        if (isSystemApp) return "App de sistema"
        if (packageName in launcherPackages) return "Launcher"
        if (packageName in keyboardPackages) return "Teclado"
        if (criticalPackageNames.contains(packageName)) return "Servicio critico"
        if (criticalPackagePrefixes.any { packageName.startsWith(it) }) return "Servicio critico"

        // Banking, authenticator and messaging apps are detected by conservative keywords.
        val searchable = "$appName $packageName".lowercase(Locale.ROOT)
        val matchedKeyword = criticalKeywords.firstOrNull { searchable.contains(it) }
        return matchedKeyword?.let { "Posible app critica" }
    }

    private fun ApplicationInfo.isSystemApp(): Boolean {
        val system = flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updatedSystem = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return system || updatedSystem
    }

    private data class UsageAggregate(
        var totalForegroundMillis: Long = 0L,
        var lastTimeUsedMillis: Long = 0L
    )

    companion object {
        private val criticalPackageNames = setOf(
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.android.systemui",
            "com.android.settings",
            "com.android.providers.downloads",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",
            "com.authy.authy",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms"
        )

        private val criticalPackagePrefixes = setOf(
            "com.android.",
            "com.google.android.gms",
            "com.google.android.gsf"
        )

        private val criticalKeywords = listOf(
            "banco",
            "bank",
            "bancolombia",
            "davivienda",
            "daviplata",
            "nequi",
            "bbva",
            "itau",
            "scotiabank",
            "paypal",
            "binance",
            "authenticator",
            "autenticador",
            "authy",
            "otp",
            "2fa",
            "duo",
            "aegis",
            "whatsapp",
            "telegram",
            "signal",
            "messages",
            "mensajes",
            "messenger"
        )
    }
}
