package com.example.appcleaneradvisor.data

import java.util.concurrent.TimeUnit

data class RecommendationResult(
    val type: RecommendationType,
    val reasons: List<String>,
    val score: Int
)

object AppRecommendationScorer {
    private val fiveMinutes = TimeUnit.MINUTES.toMillis(5)
    private val thirtyMinutes = TimeUnit.MINUTES.toMillis(30)
    private val twoHours = TimeUnit.HOURS.toMillis(2)

    fun classify(
        isSystemApp: Boolean,
        criticalReason: String?,
        lastTimeUsedMillis: Long?,
        usage7DaysMillis: Long,
        usage30DaysMillis: Long,
        usage90DaysMillis: Long,
        sizeBytes: Long?,
        firstInstallTimeMillis: Long?,
        nowMillis: Long = System.currentTimeMillis()
    ): RecommendationResult {
        // The app is advisory only, so anything critical is classified conservatively.
        if (isSystemApp || criticalReason != null) {
            val reason = criticalReason ?: "App de sistema"
            return RecommendationResult(
                type = RecommendationType.DO_NOT_TOUCH,
                reasons = listOf(reason),
                score = -100
            )
        }

        val daysSinceLastUse = lastTimeUsedMillis
            ?.takeIf { it > 0L }
            ?.let { TimeUnit.MILLISECONDS.toDays(nowMillis - it).coerceAtLeast(0L) }

        val installedDays = firstInstallTimeMillis
            ?.takeIf { it > 0L }
            ?.let { TimeUnit.MILLISECONDS.toDays(nowMillis - it).coerceAtLeast(0L) }

        val noRecentUse = when {
            daysSinceLastUse != null -> daysSinceLastUse > 60
            installedDays != null -> installedDays > 60
            else -> false
        }

        // Main delete-candidate rule requested by the app: old last use plus tiny 30-day usage.
        if (noRecentUse && usage30DaysMillis < fiveMinutes) {
            val sizeBonus = when {
                sizeBytes == null -> 0
                sizeBytes > 1_000_000_000L -> 20
                sizeBytes > 250_000_000L -> 10
                else -> 0
            }
            val idleBonus = (daysSinceLastUse ?: installedDays ?: 60L).coerceAtMost(180L).toInt() / 6
            return RecommendationResult(
                type = RecommendationType.DELETE_CANDIDATE,
                reasons = listOf("Sin uso relevante por mas de 60 dias", "Menos de 5 min en 30 dias"),
                score = 100 + idleBonus + sizeBonus
            )
        }

        if (usage30DaysMillis >= twoHours || usage7DaysMillis >= thirtyMinutes) {
            return RecommendationResult(
                type = RecommendationType.KEEP,
                reasons = listOf("Uso frecuente reciente"),
                score = 10
            )
        }

        val isLowUse = usage30DaysMillis < thirtyMinutes || (daysSinceLastUse != null && daysSinceLastUse > 30)
        if (isLowUse || usage90DaysMillis < twoHours) {
            val reason = if (daysSinceLastUse != null && daysSinceLastUse > 30) {
                "Ultimo uso hace mas de 30 dias"
            } else {
                "Uso bajo en los ultimos periodos"
            }
            return RecommendationResult(
                type = RecommendationType.REVIEW,
                reasons = listOf(reason),
                score = 50
            )
        }

        return RecommendationResult(
            type = RecommendationType.KEEP,
            reasons = listOf("Tiene uso reciente"),
            score = 5
        )
    }
}
