package com.example.appcleaneradvisor.data

import android.graphics.drawable.Drawable
import java.util.concurrent.TimeUnit

data class AppUsageInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean,
    val criticalReason: String?,
    val lastTimeUsedMillis: Long?,
    val usage7DaysMillis: Long,
    val usage30DaysMillis: Long,
    val usage90DaysMillis: Long,
    val sizeBytes: Long?,
    val firstInstallTimeMillis: Long?,
    val recommendation: RecommendationType,
    val recommendationReasons: List<String>,
    val candidateScore: Int
) {
    fun daysSinceLastUse(nowMillis: Long = System.currentTimeMillis()): Long? {
        val lastUsed = lastTimeUsedMillis?.takeIf { it > 0L } ?: return null
        return TimeUnit.MILLISECONDS.toDays(nowMillis - lastUsed).coerceAtLeast(0L)
    }
}

enum class RecommendationType(
    val label: String,
    val sortWeight: Int
) {
    DO_NOT_TOUCH("No tocar", 0),
    KEEP("Mantener", 1),
    REVIEW("Revisar", 2),
    DELETE_CANDIDATE("Candidata a borrar", 3)
}
