package com.example.appcleaneradvisor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appcleaneradvisor.data.AppRepository
import com.example.appcleaneradvisor.data.AppUsageInfo
import com.example.appcleaneradvisor.data.RecommendationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppCleanerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val _uiState = MutableStateFlow(AppCleanerUiState())
    val uiState: StateFlow<AppCleanerUiState> = _uiState.asStateFlow()

    private var allApps: List<AppUsageInfo> = emptyList()

    init {
        refreshUsagePermission()
    }

    fun refreshUsagePermission() {
        val hasAccess = repository.hasUsageAccess()
        val current = _uiState.value
        if (!hasAccess) {
            allApps = emptyList()
            _uiState.value = current.copy(
                hasUsageAccess = false,
                isLoading = false,
                visibleApps = emptyList(),
                totalApps = 0,
                errorMessage = null
            )
            return
        }

        _uiState.update { it.copy(hasUsageAccess = true) }
        if (allApps.isEmpty() && !current.isLoading) {
            reloadApps()
        }
    }

    fun reloadApps() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.loadInstalledApps() }
                .onSuccess { loadedApps ->
                    allApps = loadedApps
                    publishVisibleApps(isLoading = false)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "No se pudieron cargar las apps"
                        )
                    }
                }
        }
    }

    fun openUsageAccessSettings() {
        getApplication<Application>().startActivity(repository.usageAccessSettingsIntent())
    }

    fun openAppDetails(packageName: String) {
        getApplication<Application>().startActivity(repository.appDetailsIntent(packageName))
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { it.copy(sortOption = option) }
        publishVisibleApps()
    }

    fun setHideSystemApps(enabled: Boolean) {
        _uiState.update { it.copy(hideSystemApps = enabled) }
        publishVisibleApps()
    }

    fun setOnlyDeleteCandidates(enabled: Boolean) {
        _uiState.update { it.copy(onlyDeleteCandidates = enabled) }
        publishVisibleApps()
    }

    fun setUnusedDaysFilter(days: Int?) {
        _uiState.update { it.copy(unusedDaysFilter = days) }
        publishVisibleApps()
    }

    fun buildFullReportCsv(): String {
        val header = listOf(
            "app_name",
            "package_name",
            "app_type",
            "recommendation",
            "critical_reason",
            "last_used",
            "days_since_last_use",
            "usage_7d_minutes",
            "usage_30d_minutes",
            "usage_90d_minutes",
            "size_mb",
            "candidate_score",
            "reasons"
        )

        val rows = allApps
            .sortedWith(SortOption.BEST_DELETE_CANDIDATE.comparator)
            .map { app ->
                listOf(
                    app.appName,
                    app.packageName,
                    if (app.isSystemApp) "system" else "user",
                    app.recommendation.label,
                    app.criticalReason.orEmpty(),
                    app.lastTimeUsedMillis.toIsoText(),
                    app.daysSinceLastUse()?.toString().orEmpty(),
                    app.usage7DaysMillis.toMinutesText(),
                    app.usage30DaysMillis.toMinutesText(),
                    app.usage90DaysMillis.toMinutesText(),
                    app.sizeBytes.toMegabytesText(),
                    app.candidateScore.toString(),
                    app.recommendationReasons.joinToString(" / ")
                )
            }

        return (listOf(header) + rows)
            .joinToString(separator = "\n") { row -> row.joinToString(separator = ",") { it.csvCell() } }
    }

    fun hasReportData(): Boolean = allApps.isNotEmpty()

    private fun publishVisibleApps(isLoading: Boolean = _uiState.value.isLoading) {
        val state = _uiState.value
        val visible = allApps
            .asSequence()
            .filter { app -> !state.hideSystemApps || !app.isSystemApp }
            .filter { app -> !state.onlyDeleteCandidates || app.recommendation == RecommendationType.DELETE_CANDIDATE }
            .filter { app ->
                val threshold = state.unusedDaysFilter ?: return@filter true
                app.hasNoUseForAtLeast(threshold)
            }
            .sortedWith(state.sortOption.comparator)
            .toList()

        _uiState.value = state.copy(
            isLoading = isLoading,
            visibleApps = visible,
            totalApps = allApps.size,
            errorMessage = null
        )
    }

    private fun AppUsageInfo.hasNoUseForAtLeast(thresholdDays: Int): Boolean {
        val daysSinceLastUse = daysSinceLastUse()
        if (daysSinceLastUse != null) return daysSinceLastUse >= thresholdDays

        val installedDays = firstInstallTimeMillis
            ?.let { TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it).coerceAtLeast(0L) }

        return installedDays == null || installedDays >= thresholdDays
    }

    private fun Long?.toIsoText(): String =
        this?.takeIf { it > 0L }?.let { Instant.ofEpochMilli(it).toString() }.orEmpty()

    private fun Long.toMinutesText(): String =
        String.format(Locale.US, "%.2f", this / 60_000.0)

    private fun Long?.toMegabytesText(): String =
        this?.let { String.format(Locale.US, "%.2f", it / 1_000_000.0) }.orEmpty()

    private fun String.csvCell(): String {
        val escaped = replace("\"", "\"\"")
        return "\"$escaped\""
    }
}

data class AppCleanerUiState(
    val hasUsageAccess: Boolean = false,
    val isLoading: Boolean = false,
    val visibleApps: List<AppUsageInfo> = emptyList(),
    val totalApps: Int = 0,
    val sortOption: SortOption = SortOption.BEST_DELETE_CANDIDATE,
    val hideSystemApps: Boolean = true,
    val onlyDeleteCandidates: Boolean = false,
    val unusedDaysFilter: Int? = null,
    val errorMessage: String? = null
)

enum class SortOption(
    val label: String,
    val comparator: Comparator<AppUsageInfo>
) {
    LEAST_USED(
        "Menos usada",
        compareBy<AppUsageInfo> { it.usage30DaysMillis }
            .thenBy { it.usage90DaysMillis }
            .thenBy { it.appName.lowercase() }
    ),
    OLDEST_LAST_USED(
        "Ultimo uso mas antiguo",
        compareBy<AppUsageInfo> { it.lastTimeUsedMillis ?: 0L }
            .thenBy { it.appName.lowercase() }
    ),
    LARGEST_SIZE(
        "Mayor tamano",
        compareByDescending<AppUsageInfo> { it.sizeBytes ?: -1L }
            .thenBy { it.appName.lowercase() }
    ),
    BEST_DELETE_CANDIDATE(
        "Mejor candidata para borrar",
        compareByDescending<AppUsageInfo> { it.candidateScore }
            .thenByDescending { it.recommendation.sortWeight }
            .thenBy { it.appName.lowercase() }
    )
}
