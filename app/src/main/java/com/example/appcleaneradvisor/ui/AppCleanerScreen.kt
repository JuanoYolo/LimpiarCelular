package com.example.appcleaneradvisor.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appcleaneradvisor.data.AppUsageInfo
import com.example.appcleaneradvisor.data.RecommendationType
import com.example.appcleaneradvisor.ui.theme.AppCleanerTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AppCleanerAdvisorApp(viewModel: AppCleanerViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingReportCsv by remember { mutableStateOf<String?>(null) }
    val reportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val csv = pendingReportCsv
        pendingReportCsv = null
        if (uri == null || csv == null) return@rememberLauncherForActivityResult

        val saved = writeTextToUri(context = context, uri = uri, text = csv)
        Toast.makeText(
            context,
            if (saved) "Informe guardado" else "No se pudo guardar el informe",
            Toast.LENGTH_LONG
        ).show()
    }

    AppCleanerTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!state.hasUsageAccess) {
                UsageAccessScreen(onOpenSettings = viewModel::openUsageAccessSettings)
            } else {
                AppListScreen(
                    state = state,
                    onReload = viewModel::reloadApps,
                    onSortChange = viewModel::setSortOption,
                    onHideSystemAppsChange = viewModel::setHideSystemApps,
                    onOnlyDeleteCandidatesChange = viewModel::setOnlyDeleteCandidates,
                    onUnusedDaysFilterChange = viewModel::setUnusedDaysFilter,
                    onAppClick = viewModel::openAppDetails,
                    onExportReport = {
                        if (!viewModel.hasReportData()) {
                            Toast.makeText(context, "Primero carga la lista de apps", Toast.LENGTH_LONG).show()
                        } else {
                            pendingReportCsv = viewModel.buildFullReportCsv()
                            reportLauncher.launch(reportFileName())
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun UsageAccessScreen(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "App Cleaner Advisor",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Para calcular ultimo uso y tiempo en pantalla, activa Acceso de uso para esta app en los ajustes de Android.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Android no permite pedir este permiso con una ventana normal. Al abrir ajustes, busca App Cleaner Advisor y activa el interruptor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onOpenSettings) {
            Text("Abrir Acceso de uso")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListScreen(
    state: AppCleanerUiState,
    onReload: () -> Unit,
    onSortChange: (SortOption) -> Unit,
    onHideSystemAppsChange: (Boolean) -> Unit,
    onOnlyDeleteCandidatesChange: (Boolean) -> Unit,
    onUnusedDaysFilterChange: (Int?) -> Unit,
    onAppClick: (String) -> Unit,
    onExportReport: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("App Cleaner Advisor") },
                actions = {
                    TextButton(onClick = onReload) {
                        Text("Actualizar")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            FilterSortPanel(
                state = state,
                onSortChange = onSortChange,
                onHideSystemAppsChange = onHideSystemAppsChange,
                onOnlyDeleteCandidatesChange = onOnlyDeleteCandidatesChange,
                onUnusedDaysFilterChange = onUnusedDaysFilterChange
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${state.visibleApps.size} de ${state.totalApps} apps",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onExportReport,
                        enabled = state.totalApps > 0
                    ) {
                        Text("Guardar CSV")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.visibleApps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .clickable { onAppClick(app.packageName) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSortPanel(
    state: AppCleanerUiState,
    onSortChange: (SortOption) -> Unit,
    onHideSystemAppsChange: (Boolean) -> Unit,
    onOnlyDeleteCandidatesChange: (Boolean) -> Unit,
    onUnusedDaysFilterChange: (Int?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Orden",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            SortMenu(selected = state.sortOption, onSortChange = onSortChange)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.hideSystemApps,
                onCheckedChange = onHideSystemAppsChange
            )
            Text("Ocultar apps de sistema")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.onlyDeleteCandidates,
                onCheckedChange = onOnlyDeleteCandidatesChange
            )
            Text("Solo candidatas a borrar")
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.unusedDaysFilter == null,
                onClick = { onUnusedDaysFilterChange(null) },
                label = { Text("Todas") }
            )
            listOf(30, 60, 90).forEach { days ->
                FilterChip(
                    selected = state.unusedDaysFilter == days,
                    onClick = { onUnusedDaysFilterChange(days) },
                    label = { Text("Sin uso ${days}d") }
                )
            }
        }
    }
}

@Composable
private fun SortMenu(
    selected: SortOption,
    onSortChange: (SortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected.label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSortChange(option)
                    }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AppRow(
    app: AppUsageInfo,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(drawable = app.icon)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecommendationChip(type = app.recommendation)
                AssistChip(
                    onClick = {},
                    label = { Text(if (app.isSystemApp) "Sistema" else "Usuario") }
                )
                app.criticalReason?.let { reason ->
                    AssistChip(onClick = {}, label = { Text(reason) })
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Ultimo uso: ${formatLastUsed(app.lastTimeUsedMillis)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Uso: 7d ${formatDuration(app.usage7DaysMillis)} / 30d ${formatDuration(app.usage30DaysMillis)} / 90d ${formatDuration(app.usage90DaysMillis)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tamano aprox.: ${formatBytes(app.sizeBytes)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (app.recommendationReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.recommendationReasons.joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AppIcon(drawable: Drawable?) {
    AndroidView(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(6, 6, 6, 6)
            }
        },
        update = { imageView ->
            imageView.setImageDrawable(drawable?.constantState?.newDrawable()?.mutate() ?: drawable)
        }
    )
}

@Composable
private fun RecommendationChip(type: RecommendationType) {
    val color = when (type) {
        RecommendationType.DO_NOT_TOUCH -> Color(0xFF7B3E46)
        RecommendationType.KEEP -> Color(0xFF1E6B52)
        RecommendationType.REVIEW -> Color(0xFF7A5A12)
        RecommendationType.DELETE_CANDIDATE -> Color(0xFF9A3412)
    }
    AssistChip(
        onClick = {},
        label = { Text(type.label) },
        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
            labelColor = color,
            containerColor = color.copy(alpha = 0.12f)
        )
    )
}

private fun formatLastUsed(lastTimeUsedMillis: Long?): String {
    val last = lastTimeUsedMillis?.takeIf { it > 0L } ?: return "Sin registro"
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - last).coerceAtLeast(0L)
    return when (days) {
        0L -> "Hoy"
        1L -> "Ayer"
        in 2L..13L -> "Hace $days dias"
        else -> {
            val date = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(last))
            "Hace $days dias ($date)"
        }
    }
}

private fun formatDuration(durationMillis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMillis)
    if (minutes <= 0L) return "0 min"
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0L -> "$minutes min"
        hours < 24L -> "${hours}h ${remainingMinutes}m"
        else -> {
            val days = hours / 24
            val remainingHours = hours % 24
            "${days}d ${remainingHours}h"
        }
    }
}

private fun formatBytes(bytes: Long?): String {
    val value = bytes ?: return "No disponible"
    val mb = value / 1_000_000.0
    return if (mb >= 1000) {
        String.format(Locale.getDefault(), "%.2f GB", mb / 1000.0)
    } else {
        String.format(Locale.getDefault(), "%.0f MB", mb)
    }
}

private fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
            requireNotNull(writer) { "No output stream" }
            writer.write(text)
        }
    }.isSuccess

private fun reportFileName(): String {
    val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
    return "app-cleaner-advisor-$stamp.csv"
}
