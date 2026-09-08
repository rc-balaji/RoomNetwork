package com.laconfianza.roommapper.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laconfianza.roommapper.model.AppTab
import com.laconfianza.roommapper.model.Carrier
import com.laconfianza.roommapper.model.Room
import com.laconfianza.roommapper.model.ScanMode
import com.laconfianza.roommapper.model.ScanUiState
import com.laconfianza.roommapper.model.Spot
import com.laconfianza.roommapper.model.TestResult
import com.laconfianza.roommapper.model.UseProfile
import com.laconfianza.roommapper.viewmodel.RoomMapperViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomMapperApp(viewModel: RoomMapperViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshEnvironment()
    }

    LaunchedEffect(Unit) { viewModel.checkArSupport() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                AppHeader(onRefresh = viewModel::refreshEnvironment)
                AppNavigation(state.selectedTab, viewModel::selectTab)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!state.requiredPermissionsGranted) {
                PermissionBanner {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }
            if (!state.network.validated && state.selectedTab != AppTab.SETTINGS) {
                NetworkNotice()
            }
            Box(modifier = Modifier.weight(1f)) {
                when (state.selectedTab) {
                    AppTab.HOME -> OverviewScreen(state, viewModel)
                    AppTab.SCAN -> ScanScreen(state, viewModel)
                    AppTab.HISTORY -> HistoryScreen(state, viewModel)
                    AppTab.SETTINGS -> SettingsScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Room Mapper", fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Text(
                "Find the strongest spot in your room",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh network status")
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppNavigation(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val icons = mapOf(
        AppTab.HOME to Icons.Outlined.Home,
        AppTab.SCAN to Icons.Outlined.Map,
        AppTab.HISTORY to Icons.Outlined.History,
        AppTab.SETTINGS to Icons.Outlined.Settings
    )
    ScrollableTabRow(
        selectedTabIndex = AppTab.entries.indexOf(selected),
        edgePadding = 16.dp,
        containerColor = Color.Transparent,
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant) }
    ) {
        AppTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(tab.label) },
                icon = { Icon(icons.getValue(tab), contentDescription = null) }
            )
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.SignalCellularAlt, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Unlock radio details", fontWeight = FontWeight.SemiBold)
                Text(
                    "Allow phone and location access for accurate signal readings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onGrant) { Text("Allow") }
        }
    }
}

@Composable
private fun NetworkNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(8.dp))
        Text(
            "Connect to mobile internet before verification.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun OverviewScreen(state: ScanUiState, viewModel: RoomMapperViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroCard(state)
        SectionTitle("Your room at a glance", "Tap any point to choose where to test next")
        RoomMapCard(
            room = state.room,
            selectedSpotId = state.selectedSpotId,
            onTap = viewModel::markSpot,
            onSpotSelect = viewModel::selectSpot
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTile(
                modifier = Modifier.weight(1f),
                value = "${state.verifiedCount}",
                label = "Verified spots",
                icon = Icons.Outlined.CheckCircle
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                value = "${state.coveragePercent}%",
                label = "Room coverage",
                icon = Icons.Outlined.Map
            )
            MetricTile(
                modifier = Modifier.weight(1f),
                value = state.signal?.technology ?: "—",
                label = "Radio",
                icon = Icons.Outlined.SignalCellularAlt
            )
        }
        BestSpotCard(state.bestSpot)
        Button(
            onClick = { viewModel.selectTab(AppTab.SCAN) },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start guided scan", fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.ArrowForward, contentDescription = null)
        }
        Text(
            "Tip: hold your phone upright at the same height you normally use it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun HeroCard(state: ScanUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.room.name,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.room.isPreview) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color.White.copy(alpha = .18f)
                        ) {
                            Text(
                                "Preview",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color.White.copy(alpha = .18f)
                    ) {
                        Text(
                            "${state.room.widthMeters} × ${state.room.heightMeters} m",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Text(
                    "${state.network.networkLabel} • ${state.signal?.radioLabel ?: "Mobile signal"}",
                    color = Color.White.copy(alpha = .88f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        state.scanStatus,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricTile(modifier: Modifier, value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoomMapCard(
    room: Room,
    selectedSpotId: String?,
    onTap: (Float, Float) -> Unit,
    onSpotSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Signal map", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Tap to mark", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RoomMapCanvas(room, selectedSpotId, onTap)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                room.spots.forEach { spot ->
                    SpotLegend(spot, spot.id == selectedSpotId) { onSpotSelect(spot.id) }
                }
            }
        }
    }
}

@Composable
private fun RoomMapCanvas(room: Room, selectedSpotId: String?, onTap: (Float, Float) -> Unit) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(218.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = .06f))
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize, room.spots) {
                detectTapGestures { offset ->
                    if (canvasSize.width > 0 && canvasSize.height > 0) {
                        onTap(
                            (offset.x / canvasSize.width).coerceIn(0f, 1f),
                            (offset.y / canvasSize.height).coerceIn(0f, 1f)
                        )
                    }
                }
            }
            .semantics { contentDescription = "Room signal map. Tap anywhere to mark a test spot." }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val wall = colorScheme.primary.copy(alpha = .28f)
            val grid = colorScheme.onSurface.copy(alpha = .08f)
            val left = size.width * .06f
            val top = size.height * .08f
            val width = size.width * .88f
            val height = size.height * .84f
            drawRoundRect(
                color = colorScheme.surface,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f)
            )
            for (column in 1..4) {
                val x = left + width * column / 5f
                drawLine(grid, Offset(x, top), Offset(x, top + height), strokeWidth = 1f)
            }
            for (row in 1..3) {
                val y = top + height * row / 4f
                drawLine(grid, Offset(left, y), Offset(left + width, y), strokeWidth = 1f)
            }
            drawRoundRect(
                color = wall,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f),
                style = Stroke(width = 3f)
            )
            room.spots.forEach { spot ->
                val point = Offset(left + width * spot.x, top + height * spot.y)
                val color = spot.result?.let { scoreColor(it.score, it.error) } ?: colorScheme.outline
                if (spot.id == selectedSpotId) {
                    drawCircle(color.copy(alpha = .20f), radius = 23f, center = point)
                    drawCircle(color, radius = 17f, center = point, style = Stroke(width = 3f))
                }
                drawCircle(color, radius = 10f, center = point)
                drawCircle(Color.White, radius = 4f, center = point)
            }
        }
    }
}

@Composable
private fun SpotLegend(spot: Spot, selected: Boolean, onClick: () -> Unit) {
    val color = spot.result?.let { scoreColor(it.score, it.error) } ?: MaterialTheme.colorScheme.outline
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
    ) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
            Spacer(Modifier.width(6.dp))
            Text(spot.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            if (spot.isVerified) {
                Spacer(Modifier.width(4.dp))
                Text("${spot.result?.score ?: 0}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BestSpotCard(spot: Spot?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = .18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Best tested spot", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .75f))
                Text(
                    spot?.name ?: "No verified spot yet",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    spot?.result?.let { "${it.score}/100 • ${it.compactSpeed}" } ?: "Run a scan to compare Jio and Airtel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .8f)
                )
            }
            if (spot != null) {
                Text("${spot.result?.score ?: 0}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun ScanScreen(state: ScanUiState, viewModel: RoomMapperViewModel) {
    var showCustomBudget by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("Guided scan", "Choose a carrier, mark a spot, and verify it with a bounded test")
        SelectorSection("Carrier", "The active data route is checked before scoring") {
            Carrier.entries.forEach { carrier ->
                FilterChip(
                    selected = carrier == state.selectedCarrier,
                    onClick = { viewModel.selectCarrier(carrier) },
                    label = { Text(carrier.label) }
                )
            }
        }
        SelectorSection("Use profile", null) {
            UseProfile.entries.forEach { profile ->
                FilterChip(
                    selected = profile == state.selectedProfile,
                    onClick = { viewModel.selectProfile(profile) },
                    label = { Text(profile.label) }
                )
            }
        }
        SelectorSection("Mapping mode", "Guided mode works on every supported Android phone") {
            FilterChip(
                selected = state.selectedMode == ScanMode.GUIDED,
                onClick = { viewModel.selectMode(ScanMode.GUIDED) },
                label = { Text(ScanMode.GUIDED.label) }
            )
            FilterChip(
                selected = state.selectedMode == ScanMode.AUTO,
                enabled = state.arAvailable,
                onClick = { viewModel.selectMode(ScanMode.AUTO) },
                label = { Text(if (state.arAvailable) "Auto map assist" else "Auto map unavailable") }
            )
        }
        SelectorSection("Data budget", "One scan stays within your selected cap") {
            listOf(25, 100, 300).forEach { mb ->
                FilterChip(
                    selected = state.budgetMb == mb,
                    onClick = { viewModel.selectBudget(mb) },
                    label = { Text("${mb} MB") }
                )
            }
            FilterChip(
                selected = state.budgetMb !in listOf(25, 100, 300),
                onClick = { showCustomBudget = true },
                label = { Text(if (state.budgetMb !in listOf(25, 100, 300)) "${state.budgetMb} MB" else "Custom") }
            )
        }
        RoomMapCard(
            room = state.room,
            selectedSpotId = state.selectedSpotId,
            onTap = viewModel::markSpot,
            onSpotSelect = viewModel::selectSpot
        )
        ScanStatusCard(state)
        if (state.isScanning) {
            LinearProgressIndicator(
                progress = { state.scanProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = viewModel::cancelScan,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Stop safely")
            }
        } else {
            Button(
                onClick = viewModel::startVerification,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Verify ${state.selectedSpot?.name ?: "selected spot"}", fontWeight = FontWeight.Bold)
            }
        }
        state.selectedSpot?.let { spot ->
            spot.result?.let { result -> ResultCard(spot.name, result) }
        }
        Text(
            "Results are local to this room and moment. Walls, people, weather, and network load can change the score.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
    if (showCustomBudget) {
        CustomBudgetDialog(
            currentMb = state.budgetMb,
            onDismiss = { showCustomBudget = false },
            onApply = { mb -> viewModel.selectBudget(mb) }
        )
    }
}

@Composable
private fun SelectorSection(title: String, subtitle: String?, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun ScanStatusCard(state: ScanUiState) {
    val isError = state.errorMessage != null || (!state.network.validated && state.isScanning)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
        )
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    if (isError) Icons.Outlined.Warning else Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.scanStatus, fontWeight = FontWeight.SemiBold)
                Text(
                    "${state.signal?.strengthLabel ?: "Signal details unavailable"} • ${state.network.transport}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultCard(spotName: String, result: TestResult) {
    val color = scoreColor(result.score, result.error)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(spotName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(result.statusLabel, style = MaterialTheme.typography.bodySmall, color = color)
                }
                Text("${result.score}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = color)
                Text("/100", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResultMetric("Download", result.downloadMbps?.let { "${formatDecimal(it)} Mbps" } ?: "—")
                ResultMetric("Upload", result.uploadMbps?.let { "${formatDecimal(it)} Mbps" } ?: "—")
                ResultMetric("Latency", result.latencyMs?.let { "$it ms" } ?: "—")
            }
            Text(
                "${result.reliabilityPercent}% reliable • ${result.bytesUsed / 1024} KB used${if (!result.routeVerified) " • route not confirmed" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowScope.ResultMetric(label: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HistoryScreen(state: ScanUiState, viewModel: RoomMapperViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Scan history", "Private on this phone • newest first")
            Spacer(Modifier.weight(1f))
            if (state.history.isNotEmpty()) {
                IconButton(onClick = viewModel::clearHistory) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Clear scan history")
                }
            }
        }
        if (state.history.isEmpty()) {
            EmptyHistoryCard { viewModel.selectTab(AppTab.SCAN) }
        } else {
            state.history.forEach { entry ->
                HistoryRow(entry.roomName, entry.spotName, entry.carrier.label, entry.score, entry.status, entry.formattedDate)
            }
        }
        PrivacyCard()
    }
}

@Composable
private fun EmptyHistoryCard(onScan: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No saved scans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Verify a few spots and compare them here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onScan) { Text("Go to scan") }
        }
    }
}

@Composable
private fun HistoryRow(roomName: String, spotName: String, carrier: String, score: Int, status: String, date: String) {
    val color = scoreColor(score, null)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color.copy(alpha = .13f)),
                contentAlignment = Alignment.Center
            ) { Text("$score", fontWeight = FontWeight.Bold, color = color) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(spotName, fontWeight = FontWeight.SemiBold)
                Text("$roomName • $carrier", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$status • $date", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: ScanUiState, viewModel: RoomMapperViewModel) {
    var showCustomBudget by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle("Settings", "Make every scan predictable and private")
        SettingsCard(Icons.Outlined.Security, "Privacy by default") {
            Text("Room names, spots, and scan history stay in this app's private storage. No account is required.", style = MaterialTheme.typography.bodyMedium)
            Text("Network tests use small bounded requests and show the data used after each run.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsCard(Icons.Outlined.Speed, "Data budget") {
            Text("Maximum transfer allowance per verification", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 100, 300).forEach { mb ->
                    FilterChip(selected = state.budgetMb == mb, onClick = { viewModel.selectBudget(mb) }, label = { Text("${mb} MB") })
                }
                FilterChip(
                    selected = state.budgetMb !in listOf(25, 100, 300),
                    onClick = { showCustomBudget = true },
                    label = { Text(if (state.budgetMb !in listOf(25, 100, 300)) "${state.budgetMb} MB" else "Custom") }
                )
            }
        }
        SettingsCard(Icons.Outlined.Map, "Mapping mode") {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.selectedMode == ScanMode.GUIDED, onClick = { viewModel.selectMode(ScanMode.GUIDED) }, label = { Text("Guided") })
                FilterChip(selected = state.selectedMode == ScanMode.AUTO, enabled = state.arAvailable, onClick = { viewModel.selectMode(ScanMode.AUTO) }, label = { Text("Auto assist") })
            }
            Text(
                if (state.arAvailable) "ARCore is available on this phone; guided pins remain the reliable fallback." else "ARCore is not available; guided pins work without special hardware.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingsCard(Icons.Outlined.Info, "About Room Mapper") {
            Text("Version 1.0.0", fontWeight = FontWeight.SemiBold)
            Text("Designed for quick, honest room-level comparisons across Jio, Airtel, and other mobile routes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Signal strength depends on phone model, Android version, carrier permissions, and local conditions. Treat every score as a point-in-time measurement.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
    if (showCustomBudget) {
        CustomBudgetDialog(
            currentMb = state.budgetMb,
            onDismiss = { showCustomBudget = false },
            onApply = { mb -> viewModel.selectBudget(mb) }
        )
    }
}

@Composable
private fun CustomBudgetDialog(currentMb: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var value by remember(currentMb) { mutableStateOf(currentMb.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom data budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose between 5 and 1000 MB for one verification.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = value,
                    onValueChange = { next -> if (next.length <= 4 && next.all { it.isDigit() }) value = next },
                    label = { Text("Megabytes") },
                    suffix = { Text("MB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    value.toIntOrNull()?.coerceIn(5, 1000)?.let(onApply)
                    onDismiss()
                }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text(title, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Your data stays local", fontWeight = FontWeight.SemiBold)
                Text("Delete history any time from this screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun scoreColor(score: Int, error: String?): Color = when {
    error != null -> Color(0xFFB3261E)
    score >= 80 -> Color(0xFF16835B)
    score >= 60 -> Color(0xFFB26A00)
    else -> Color(0xFFB3261E)
}

private fun formatDecimal(value: Double): String = if (value >= 10) "%.0f".format(value) else "%.1f".format(value)
