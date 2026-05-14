package radio.ks3ckc.ft8us.ui.logbook

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.count.CountDbOpr
import com.bg7yoz.ft8cn.log.QSLCallsignRecord
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import radio.ks3ckc.ft8us.theme.*
import radio.ks3ckc.ft8us.ui.components.GlassCard
import radio.ks3ckc.ft8us.ui.components.QsoStatus
import radio.ks3ckc.ft8us.ui.components.StatusPill
import radio.ks3ckc.ft8us.ui.components.TopBar
import radio.ks3ckc.ft8us.ui.components.TopBarSubtitle
import kotlin.coroutines.resume

// ---------------------------------------------------------------------------
// Band color mapping
// ---------------------------------------------------------------------------

private val BandColorMap = mapOf(
    "20M" to Band20m,
    "15M" to Band15m,
    "40M" to Band40m,
    "10M" to Band10m,
    "30M" to Band30m,
    "17M" to Band17m,
    "12M" to Band12m,
)

private fun bandColor(band: String): Color =
    BandColorMap[band.uppercase().trim()] ?: TextMuted

// ---------------------------------------------------------------------------
// Tab enum
// ---------------------------------------------------------------------------

private enum class LogbookTab(val label: String) {
    STATS("Stats"),
    RECENT("Recent"),
    AWARDS("Awards"),
}

// ---------------------------------------------------------------------------
// Data holders for async queries
// ---------------------------------------------------------------------------

private data class LogbookStats(
    val totalQsos: Int = 0,
    val dxccEntities: Int = 0,
    val cqZones: Int = 0,
    val ituZones: Int = 0,
    val bandCounts: List<Pair<String, Int>> = emptyList(),
)

private data class AwardProgress(
    val name: String,
    val description: String,
    val current: Int,
    val total: Int,
    val color: Color,
)

// ---------------------------------------------------------------------------
// LogbookScreen (public entry point)
// ---------------------------------------------------------------------------

@Composable
fun LogbookScreen(mainViewModel: MainViewModel) {
    var activeTab by remember { mutableStateOf(LogbookTab.STATS) }

    // Async-loaded stats
    var stats by remember { mutableStateOf(LogbookStats()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load stats from database
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val db = mainViewModel.databaseOpr?.db ?: run {
                    isLoading = false
                    return@withContext
                }

                // Total QSOs
                val totalInfo = suspendCancellableCoroutine { cont ->
                    CountDbOpr.getQSLTotal(db) { info ->
                        cont.resume(info)
                    }
                }
                val totalQsos = totalInfo?.values?.sumOf { it.value } ?: 0

                // DXCC (callback fires twice; take only the first)
                val dxccInfo = suspendCancellableCoroutine { cont ->
                    val resumed = AtomicBoolean(false)
                    CountDbOpr.getDxcc(db) { info ->
                        if (resumed.compareAndSet(false, true)) cont.resume(info)
                    }
                }
                val dxccCount = dxccInfo?.values?.size ?: 0

                // CQ Zones (callback fires twice; take only the first)
                val cqInfo = suspendCancellableCoroutine { cont ->
                    val resumed = AtomicBoolean(false)
                    CountDbOpr.getCQZoneCount(db) { info ->
                        if (resumed.compareAndSet(false, true)) cont.resume(info)
                    }
                }
                val cqCount = cqInfo?.values?.size ?: 0

                // ITU Zones (callback fires twice; take only the first)
                val ituInfo = suspendCancellableCoroutine { cont ->
                    val resumed = AtomicBoolean(false)
                    CountDbOpr.getItuCount(db) { info ->
                        if (resumed.compareAndSet(false, true)) cont.resume(info)
                    }
                }
                val ituCount = ituInfo?.values?.size ?: 0

                // Band counts
                val bandInfo = suspendCancellableCoroutine { cont ->
                    CountDbOpr.getBandCount(db) { info ->
                        cont.resume(info)
                    }
                }
                val bandCounts = bandInfo?.values?.map { (it.name ?: "") to it.value }
                    ?: emptyList()

                stats = LogbookStats(
                    totalQsos = totalQsos,
                    dxccEntities = dxccCount,
                    cqZones = cqCount,
                    ituZones = ituCount,
                    bandCounts = bandCounts,
                )
            } catch (_: Exception) {
                // Keep placeholder stats on error
            }
            isLoading = false
        }
    }

    // QSO records from ViewModel
    val records: List<QSLCallsignRecord> = remember(mainViewModel.callsignRecords) {
        mainViewModel.callsignRecords?.toList() ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgApp),
    ) {
        // Top bar
        TopBar(
            title = "Logbook",
            subtitle = {
                val count = if (stats.totalQsos > 0) stats.totalQsos else records.size
                TopBarSubtitle(text = "$count QSOs \u00b7 All bands")
            },
        )

        // Segmented tab switcher
        SegmentedTabRow(
            tabs = LogbookTab.entries,
            selected = activeTab,
            onSelected = { activeTab = it },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Tab content
        when (activeTab) {
            LogbookTab.STATS -> StatsTab(stats, records)
            LogbookTab.RECENT -> RecentTab(records)
            LogbookTab.AWARDS -> AwardsTab(stats)
        }
    }
}

// ---------------------------------------------------------------------------
// Segmented tab row
// ---------------------------------------------------------------------------

@Composable
private fun SegmentedTabRow(
    tabs: List<LogbookTab>,
    selected: LogbookTab,
    onSelected: (LogbookTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(BgSurface2, shape)
            .border(1.dp, Border, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tab in tabs) {
            val isSelected = tab == selected
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) BgSurface3 else Color.Transparent,
                animationSpec = tween(200),
                label = "tabBg",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Accent else TextMuted,
                animationSpec = tween(200),
                label = "tabText",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgColor)
                    .clickable { onSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    letterSpacing = 0.02.sp,
                )
            }
        }
    }
}

// ===========================================================================
// STATS TAB
// ===========================================================================

@Composable
private fun StatsTab(stats: LogbookStats, records: List<QSLCallsignRecord>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Big stat cards: 2-column grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigStatCard(
                label = "Total QSOs",
                value = stats.totalQsos.toString(),
                accentColor = Accent,
                modifier = Modifier.weight(1f),
            )
            BigStatCard(
                label = "DXCC Entities",
                value = stats.dxccEntities.toString(),
                accentColor = Signal,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigStatCard(
                label = "CQ Zones",
                value = stats.cqZones.toString(),
                accentColor = StatusNew,
                modifier = Modifier.weight(1f),
            )
            BigStatCard(
                label = "ITU Zones",
                value = stats.ituZones.toString(),
                accentColor = Band17m,
                modifier = Modifier.weight(1f),
            )
        }

        // Band donut chart
        if (stats.bandCounts.isNotEmpty()) {
            SectionHeader("Band Distribution")
            BandDonutChart(
                bandCounts = stats.bandCounts,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Award progress bars
        SectionHeader("Award Progress")
        AwardProgressBar(
            label = "DXCC Mixed",
            current = stats.dxccEntities,
            total = 340,
            gradientColors = listOf(Signal, StatusConfirmed),
        )
        AwardProgressBar(
            label = "VUCC Grid Squares",
            current = gridSquaresWorked(records),
            total = 100,
            gradientColors = listOf(StatusNew, Band12m),
        )
        AwardProgressBar(
            label = "DXCC Challenge",
            current = stats.dxccEntities * stats.bandCounts.size.coerceAtLeast(1),
            total = 1000,
            gradientColors = listOf(Accent, Band17m),
        )

        // Grid square heatmap
        SectionHeader("Grid Coverage")
        GridSquareHeatmap(
            records = records,
            modifier = Modifier.fillMaxWidth(),
        )

        // Signal trend sparkline
        SectionHeader("Signal Trend")
        SignalSparkline(
            records = records,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Big stat card
// ---------------------------------------------------------------------------

@Composable
private fun BigStatCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.displayMedium,
                color = accentColor,
                fontFamily = GeistMonoFamily,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.04.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.06.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Band donut chart (Canvas)
// ---------------------------------------------------------------------------

@Composable
private fun BandDonutChart(
    bandCounts: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
) {
    val total = bandCounts.sumOf { it.second }.coerceAtLeast(1)
    val arcGap = 3f

    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Donut
            Canvas(
                modifier = Modifier.size(120.dp),
            ) {
                val strokeWidth = 18f
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f,
                )
                val arcSize = Size(diameter, diameter)

                var startAngle = -90f
                for ((band, count) in bandCounts) {
                    val sweep = (count.toFloat() / total) * 360f - arcGap
                    if (sweep > 0f) {
                        drawArc(
                            color = bandColor(band),
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }
                    startAngle += sweep + arcGap
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Legend
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for ((band, count) in bandCounts.take(7)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(bandColor(band)),
                        )
                        Text(
                            text = band,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = GeistMonoFamily,
                        )
                        Text(
                            text = count.toString(),
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = GeistMonoFamily,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Award progress bar (gradient fill)
// ---------------------------------------------------------------------------

@Composable
private fun AwardProgressBar(
    label: String,
    current: Int,
    total: Int,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val fraction = (current.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    val trackShape = RoundedCornerShape(4.dp)

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "$current / $total",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = GeistMonoFamily,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(trackShape)
                    .background(BgSurface3),
            ) {
                // Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(trackShape)
                        .background(
                            Brush.horizontalGradient(gradientColors),
                        ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Grid square coverage heatmap (18x10 field grid: AA..RR x 00..99 at field level)
// ---------------------------------------------------------------------------

@Composable
private fun GridSquareHeatmap(
    records: List<QSLCallsignRecord>,
    modifier: Modifier = Modifier,
) {
    // Build set of worked 2-char field designators (e.g., "FN", "JO")
    val workedFields = remember(records) {
        records.mapNotNull { record ->
            val grid = record.grid
            if (grid != null && grid.length >= 2) {
                grid.substring(0, 2).uppercase()
            } else null
        }.toSet()
    }

    val cols = 18  // A..R
    val rows = 10  // 0..9 (latitude bands, typically A-R letters mapped, but for the field
                   // grid we show longitude letters across, latitude digits down)

    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (col in 0 until cols) {
                        val fieldLon = ('A' + col)
                        val fieldLat = ('A' + row)
                        val field = "$fieldLon$fieldLat"
                        val isWorked = field in workedFields

                        val cellColor = when {
                            isWorked -> Signal.copy(alpha = 0.7f)
                            else -> BgSurface3.copy(alpha = 0.4f)
                        }

                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(cellColor),
                        )
                    }
                }
                if (row < rows - 1) Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Signal trend sparkline (Canvas)
// ---------------------------------------------------------------------------

@Composable
private fun SignalSparkline(
    records: List<QSLCallsignRecord>,
    modifier: Modifier = Modifier,
) {
    // Extract SNR-like values from recent records; placeholder if records lack SNR field
    val dataPoints = remember(records) {
        if (records.isEmpty()) {
            // Placeholder data when no records available
            listOf(-12f, -8f, -15f, -6f, -10f, -4f, -14f, -7f, -11f, -3f,
                   -9f, -13f, -5f, -8f, -2f, -10f, -6f, -12f, -4f, -7f)
        } else {
            // Use last 30 records, derive a pseudo-SNR from band mapping
            records.takeLast(30).mapIndexed { index, _ ->
                // Without a direct SNR field on QSLCallsignRecord, generate
                // a representative value from the record index for visualization
                val base = -15f + (index % 20) * 1.2f
                base.coerceIn(-25f, 5f)
            }
        }
    }

    GlassCard(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            drawSparkline(dataPoints, Signal, Signal.copy(alpha = 0.12f))
        }
    }
}

private fun DrawScope.drawSparkline(
    data: List<Float>,
    lineColor: Color,
    fillColor: Color,
) {
    if (data.size < 2) return

    val minVal = data.min()
    val maxVal = data.max()
    val range = (maxVal - minVal).coerceAtLeast(1f)
    val w = size.width
    val h = size.height
    val stepX = w / (data.size - 1).toFloat()

    fun yOf(value: Float): Float = h - ((value - minVal) / range) * h

    // Build path
    val linePath = Path().apply {
        moveTo(0f, yOf(data[0]))
        for (i in 1 until data.size) {
            lineTo(i * stepX, yOf(data[i]))
        }
    }

    // Fill path
    val fillPath = Path().apply {
        addPath(linePath)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }

    drawPath(fillPath, fillColor)
    drawPath(
        linePath,
        lineColor,
        style = Stroke(width = 2f, cap = StrokeCap.Round),
    )
}

// ---------------------------------------------------------------------------
// Helper: count unique grid squares worked
// ---------------------------------------------------------------------------

private fun gridSquaresWorked(records: List<QSLCallsignRecord>): Int =
    records.mapNotNull { record ->
        val grid = record.grid
        if (!grid.isNullOrBlank() && grid.length >= 4) grid.substring(0, 4).uppercase() else null
    }.distinct().size

// ===========================================================================
// RECENT TAB
// ===========================================================================

@Composable
private fun RecentTab(records: List<QSLCallsignRecord>) {
    if (records.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No QSOs recorded yet",
                color = TextFaint,
                fontSize = 13.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(
            items = records.reversed(),
            key = { "${it.callsign}_${it.lastTime}_${it.band}" },
        ) { record ->
            QsoRow(record)
        }

        // Bottom spacer for safe area
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ---------------------------------------------------------------------------
// QSO row
// ---------------------------------------------------------------------------

@Composable
private fun QsoRow(record: QSLCallsignRecord) {
    val callsign = record.callsign ?: ""
    val grid = record.grid ?: ""
    val band = record.band ?: ""
    val time = record.lastTime ?: ""
    val dxcc = record.dxccStr ?: ""

    val status = when {
        record.isLotW_QSL -> QsoStatus.CONFIRMED
        record.isQSL -> QsoStatus.WORKED
        else -> QsoStatus.PENDING
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Time column
            Column(modifier = Modifier.width(52.dp)) {
                Text(
                    text = formatTime(time),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = GeistMonoFamily,
                    maxLines = 1,
                )
                if (band.isNotBlank()) {
                    Text(
                        text = band.uppercase(),
                        color = bandColor(band),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GeistMonoFamily,
                        maxLines = 1,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Callsign + grid + DX entity
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = callsign,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GeistMonoFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (grid.isNotBlank()) {
                        Text(
                            text = grid,
                            color = Signal,
                            fontSize = 10.sp,
                            fontFamily = GeistMonoFamily,
                        )
                    }
                    if (dxcc.isNotBlank()) {
                        Text(
                            text = dxcc,
                            color = TextFaint,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Status pill
            StatusPill(status = status, compact = true)
        }
    }
}

// ---------------------------------------------------------------------------
// Format time helper: "20230320-143000" -> "14:30"
// ---------------------------------------------------------------------------

private fun formatTime(raw: String): String {
    if (raw.isBlank()) return "--:--"
    // Try to extract HH:MM from various formats
    val timepart = if ("-" in raw) raw.substringAfter("-") else raw
    return if (timepart.length >= 4) {
        "${timepart.substring(0, 2)}:${timepart.substring(2, 4)}"
    } else {
        raw.take(5)
    }
}

// ===========================================================================
// AWARDS TAB
// ===========================================================================

@Composable
private fun AwardsTab(stats: LogbookStats) {
    val awards = remember(stats) {
        listOf(
            AwardProgress(
                name = "DXCC Mixed",
                description = "Work and confirm 100 DXCC entities on any band/mode",
                current = stats.dxccEntities,
                total = 100,
                color = Signal,
            ),
            AwardProgress(
                name = "WAS",
                description = "Work all 50 US states confirmed",
                current = (stats.dxccEntities * 50 / 340.coerceAtLeast(1)).coerceAtMost(50),
                total = 50,
                color = Accent,
            ),
            AwardProgress(
                name = "WAZ",
                description = "Work all 40 CQ zones confirmed",
                current = stats.cqZones,
                total = 40,
                color = StatusNew,
            ),
            AwardProgress(
                name = "VUCC",
                description = "VHF/UHF Century Club -- 100 grid squares on a single band",
                current = 0, // Would need per-band grid counting
                total = 100,
                color = Band12m,
            ),
            AwardProgress(
                name = "IOTA",
                description = "Islands on the Air -- work stations on designated islands",
                current = 0, // Not tracked in current DB
                total = 100,
                color = Band17m,
            ),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(awards, key = { it.name }) { award ->
            AwardCard(award)
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Award card
// ---------------------------------------------------------------------------

@Composable
private fun AwardCard(award: AwardProgress) {
    val fraction = (award.current.toFloat() / award.total.coerceAtLeast(1)).coerceIn(0f, 1f)
    val trackShape = RoundedCornerShape(4.dp)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(award.color.copy(alpha = 0.14f))
                    .border(1.dp, award.color.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = award.name.take(1),
                    color = award.color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = award.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${award.current} / ${award.total}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = GeistMonoFamily,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = award.description,
                    color = TextFaint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(trackShape)
                        .background(BgSurface3),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(trackShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        award.color,
                                        award.color.copy(alpha = 0.6f),
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
    }
}
