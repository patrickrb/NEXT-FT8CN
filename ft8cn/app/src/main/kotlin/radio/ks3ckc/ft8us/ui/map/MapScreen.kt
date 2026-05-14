package radio.ks3ckc.ft8us.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bg7yoz.ft8cn.Ft8Message
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.maidenhead.MaidenheadGrid
import radio.ks3ckc.ft8us.theme.*
import radio.ks3ckc.ft8us.ui.components.GlassCard
import radio.ks3ckc.ft8us.ui.components.TopBar
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// Data structures
// ---------------------------------------------------------------------------

private data class StationMarker(
    val callsign: String,
    val grid: String,
    val lat: Double,
    val lon: Double,
    val snr: Int,
    val isCQ: Boolean,
    val isWorked: Boolean,
    val isToMe: Boolean,
    val color: Color,
    val message: Ft8Message,
)

private data class ProjectedPoint(
    val x: Float,
    val y: Float,
    val distKm: Double,
)

// ---------------------------------------------------------------------------
// Azimuthal equidistant projection
// ---------------------------------------------------------------------------

private fun azProject(opLat: Double, opLon: Double, lat: Double, lon: Double): ProjectedPoint {
    val phi1 = Math.toRadians(opLat)
    val lam1 = Math.toRadians(opLon)
    val phi2 = Math.toRadians(lat)
    val lam2 = Math.toRadians(lon)
    val dLam = lam2 - lam1

    val cosC = sin(phi1) * sin(phi2) + cos(phi1) * cos(phi2) * cos(dLam)
    val c = acos(cosC.coerceIn(-1.0, 1.0))

    if (c < 1e-10) {
        return ProjectedPoint(0f, 0f, 0.0)
    }

    val k = c / sin(c)
    val x = k * cos(phi2) * sin(dLam)
    val y = k * (cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLam))

    return ProjectedPoint(
        x = (x / PI).toFloat(),
        y = (-y / PI).toFloat(),
        distKm = c * 6371.0,
    )
}

// ---------------------------------------------------------------------------
// Land map data (5-degree grid, 36 rows x 72 cols)
// ---------------------------------------------------------------------------

private val LAND_MAP = arrayOf(
    "000000000000000000000000000000000000000000000000000000000000000000000000",
    "000000000000000000000000000111100000000000000000000000000000000000000000",
    "000000000000111110000001111111111100000000011110000000000000000000000000",
    "000000000011111111100011111111111111000000111111100000000000000000000000",
    "000000000011111111111111111111111111100001111111110000000000000000000000",
    "000000001111111111111111111111111111110011111111111100000000000000000000",
    "000000001111111111111111111111111111111111111111111100000000000000000000",
    "000000011111111111111111111111111111111111111111111110000000000000000000",
    "000000011111111111111111111111111111111111111111111110000000000000000000",
    "000000011111111111111111111111111111111111111111111111000000000000000000",
    "000000011111111111111111111111111111111111111111111111100000001000000000",
    "000000011111111111111111111111111111111111111111111111100000011100000000",
    "000000001111111111111111111111111111111111111111111111110000011100000000",
    "000000000011111111111111111111111111111111111111111111110001111000000000",
    "000000000001111111111111111111111111111111111111111111100001110000000000",
    "000000000000111111111111111111111111111111111111111111000001110000000000",
    "000000000000011111100011111111110111111111011111111100000001110000000000",
    "000000000000001111100001111111100011110010001111111000000001100000000000",
    "000000000000000011100000111111000001100000000111110000000011000000000000",
    "000000000000000001000000011110000001100000000011100000000111000000000000",
    "000000000000000000000000001100000001100000000001000000001110000000000000",
    "000000000000000000000000001100000001110000000000000000011100000000000000",
    "000000000000000000000000000100000000110000000000000000111000000000000000",
    "000000000000000000000000000000000000110000000000000001110000000000000000",
    "000000000000000000000000000000000000010000000000000011100000000000000000",
    "000000000000000000000000000000000000000000000000000111100000000000000000",
    "000000000000000000000000000000000000000000000000001111000000000000000000",
    "000000000000000000000000000000000000000000000000011110000000000000000000",
    "000000000000000000000000000000000000000000000000111100000000000000000000",
    "000000000000000000000000000000000000000000000001111100000000000000000000",
    "000000000000000000000000000000000000000000000001111100000000000000000000",
    "000000000000000000000000000000000000000000000011111000000000000000000000",
    "000000000000000000000000000000000011111111111111111111111111111100000000",
    "000000000000000000000000001111111111111111111111111111111111111111000000",
    "000000000000000000001111111111111111111111111111111111111111111111110000",
    "111111111111111111111111111111111111111111111111111111111111111111111111",
)

private val LAND_POINTS: List<Pair<Double, Double>> by lazy {
    val points = mutableListOf<Pair<Double, Double>>()
    for (row in LAND_MAP.indices) {
        val lat = 90.0 - row * 5.0
        val line = LAND_MAP[row]
        for (col in line.indices) {
            if (line[col] == '1') {
                val lon = -180.0 + col * 5.0
                points.add(Pair(lat, lon))
            }
        }
    }
    points
}

// ---------------------------------------------------------------------------
// Map Screen
// ---------------------------------------------------------------------------

@Composable
fun MapScreen(mainViewModel: MainViewModel) {
    val messages by mainViewModel.mutableFt8MessageList.observeAsState(arrayListOf())
    val myGrid by GeneralVariables.mutableMyMaidenheadGrid.observeAsState(
        GeneralVariables.getMyMaidenheadGrid() ?: ""
    )

    var selectedCallsign by remember { mutableStateOf<String?>(null) }

    // Derive operator lat/lon from grid
    val opLatLng = remember(myGrid) {
        if (myGrid.isNullOrEmpty()) null
        else try { MaidenheadGrid.gridToLatLng(myGrid) } catch (_: Exception) { null }
    }
    val opLat = opLatLng?.latitude ?: 0.0
    val opLon = opLatLng?.longitude ?: 0.0

    // Build station markers from decoded messages (deduplicate by callsign)
    val stations = remember(messages) {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<StationMarker>()
        for (msg in messages) {
            val call = msg.callsignFrom ?: continue
            if (call in seen) continue
            seen.add(call)

            val grid = msg.maidenGrid ?: continue
            val latLng = try {
                MaidenheadGrid.gridToLatLng(grid)
            } catch (_: Exception) { continue }
            if (latLng == null) continue

            val isCQ = msg.checkIsCQ()
            val isWorked = msg.isQSL_Callsign
            val isToMe = GeneralVariables.checkIsMyCallsign(msg.callsignTo ?: "")

            val color = when {
                isToMe -> Signal
                isWorked -> StatusWorked
                isCQ && !isWorked -> Accent
                else -> StatusNew
            }

            result.add(
                StationMarker(
                    callsign = call,
                    grid = grid,
                    lat = latLng.latitude,
                    lon = latLng.longitude,
                    snr = msg.snr,
                    isCQ = isCQ,
                    isWorked = isWorked,
                    isToMe = isToMe,
                    color = color,
                    message = msg,
                )
            )
        }
        result
    }

    val selectedStation = stations.find { it.callsign == selectedCallsign }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgApp),
    ) {
        TopBar(title = "Map") {
            Text(
                text = if (myGrid.isNullOrEmpty()) "No grid set" else myGrid,
                color = Signal,
                fontFamily = GeistMonoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // Map canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            AzimuthalMapCanvas(
                opLat = opLat,
                opLon = opLon,
                stations = stations,
                selectedCallsign = selectedCallsign,
                onStationSelected = { selectedCallsign = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }

        // Selected station info card
        if (selectedStation != null) {
            SelectedStationCard(
                station = selectedStation,
                opLat = opLat,
                opLon = opLon,
                onDismiss = { selectedCallsign = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Station count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${stations.size} stations",
                color = TextMuted,
                fontSize = 10.5.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Azimuthal Equidistant",
                color = TextDim,
                fontSize = 9.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Azimuthal map canvas
// ---------------------------------------------------------------------------

@Composable
private fun AzimuthalMapCanvas(
    opLat: Double,
    opLon: Double,
    stations: List<StationMarker>,
    selectedCallsign: String?,
    onStationSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f * 0.92f

        // Background circle
        drawCircle(
            color = BgSurface,
            radius = r,
            center = Offset(cx, cy),
        )

        // Land dots
        drawLandDots(opLat, opLon, cx, cy, r)

        // Range rings
        drawRangeRings(cx, cy, r)

        // Compass bearings
        drawCompassBearings(cx, cy, r)

        // Operator center marker
        drawCircle(
            color = Accent,
            radius = 4f,
            center = Offset(cx, cy),
        )

        // Station markers
        for (station in stations) {
            val proj = azProject(opLat, opLon, station.lat, station.lon)
            val sx = cx + proj.x * r
            val sy = cy + proj.y * r

            // Check if within map circle
            val dx = sx - cx
            val dy = sy - cy
            if (sqrt(dx * dx + dy * dy) > r) continue

            val isSelected = station.callsign == selectedCallsign
            val markerR = if (isSelected) 5f else 3.5f
            val glowR = if (isSelected) 12f else 8f

            // Bearing line for selected station
            if (isSelected) {
                drawLine(
                    color = station.color.copy(alpha = 0.4f),
                    start = Offset(cx, cy),
                    end = Offset(sx, sy),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
            }

            // Glow
            drawCircle(
                color = station.color.copy(alpha = if (isSelected) 0.25f else 0.15f),
                radius = glowR,
                center = Offset(sx, sy),
            )

            // Marker dot
            drawCircle(
                color = station.color,
                radius = markerR,
                center = Offset(sx, sy),
            )
            drawCircle(
                color = BgApp,
                radius = markerR,
                center = Offset(sx, sy),
                style = Stroke(width = 1.2f),
            )

            // Callsign label
            val textColor = if (isSelected) {
                android.graphics.Color.WHITE
            } else {
                android.graphics.Color.argb(180, 138, 150, 177) // TextMuted
            }
            val paint = android.graphics.Paint().apply {
                color = textColor
                textSize = if (isSelected) 24f else 20f
                isAntiAlias = true
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.NORMAL,
                )
            }
            drawContext.canvas.nativeCanvas.drawText(
                station.callsign,
                sx + glowR + 4f,
                sy + paint.textSize / 3f,
                paint,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Drawing helpers
// ---------------------------------------------------------------------------

private fun DrawScope.drawLandDots(
    opLat: Double,
    opLon: Double,
    cx: Float,
    cy: Float,
    r: Float,
) {
    val landColor = Color(0x1A94A3B8) // subtle gray
    for ((lat, lon) in LAND_POINTS) {
        val proj = azProject(opLat, opLon, lat, lon)
        val px = cx + proj.x * r
        val py = cy + proj.y * r

        val dx = px - cx
        val dy = py - cy
        if (sqrt(dx * dx + dy * dy) > r) continue

        drawCircle(
            color = landColor,
            radius = 2.5f,
            center = Offset(px, py),
        )
    }
}

private fun DrawScope.drawRangeRings(cx: Float, cy: Float, r: Float) {
    val maxKm = 20015.0
    val rings = listOf(2500, 5000, 10000, 15000, 20000)
    val ringColor = Color(0x1894A3B8)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))

    for (km in rings) {
        val ringR = (km.toFloat() / maxKm.toFloat()) * r * (PI.toFloat() / 2f)
        drawCircle(
            color = ringColor,
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(width = 1f, pathEffect = dashEffect),
        )
    }
}

private fun DrawScope.drawCompassBearings(cx: Float, cy: Float, r: Float) {
    val directions = listOf(
        Triple("N", 0.0, true),
        Triple("NE", 45.0, false),
        Triple("E", 90.0, true),
        Triple("SE", 135.0, false),
        Triple("S", 180.0, true),
        Triple("SW", 225.0, false),
        Triple("W", 270.0, true),
        Triple("NW", 315.0, false),
    )

    for ((label, angle, isCardinal) in directions) {
        val rad = Math.toRadians(angle - 90.0) // -90 to align N with top
        val lineColor = if (isCardinal) {
            Color(0x30FFAF5E) // accent tint
        } else {
            Color(0x1894A3B8)
        }

        val endX = cx + cos(rad).toFloat() * r
        val endY = cy + sin(rad).toFloat() * r

        drawLine(
            color = lineColor,
            start = Offset(cx, cy),
            end = Offset(endX, endY),
            strokeWidth = if (isCardinal) 1f else 0.5f,
        )

        // Label
        val labelR = r + 12f
        val lx = cx + cos(rad).toFloat() * labelR
        val ly = cy + sin(rad).toFloat() * labelR

        val paint = android.graphics.Paint().apply {
            color = if (isCardinal) {
                android.graphics.Color.argb(200, 255, 175, 94) // Accent
            } else {
                android.graphics.Color.argb(100, 148, 163, 184)
            }
            textSize = if (isCardinal) 22f else 18f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.SANS_SERIF,
                if (isCardinal) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
            )
        }
        drawContext.canvas.nativeCanvas.drawText(
            label,
            lx,
            ly + paint.textSize / 3f,
            paint,
        )
    }
}

// ---------------------------------------------------------------------------
// Selected station info card
// ---------------------------------------------------------------------------

@Composable
private fun SelectedStationCard(
    station: StationMarker,
    opLat: Double,
    opLon: Double,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val proj = azProject(opLat, opLon, station.lat, station.lon)
    val bearing = computeBearing(opLat, opLon, station.lat, station.lon)

    GlassCard(modifier = modifier.clickable { onDismiss() }) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = station.callsign,
                    color = station.color,
                    fontFamily = GeistMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    text = station.grid,
                    color = TextMuted,
                    fontFamily = GeistMonoFamily,
                    fontSize = 12.sp,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InfoChip("${String.format("%.0f", proj.distKm)} km", "Distance")
                InfoChip("${String.format("%.0f", bearing)}\u00B0", "Bearing")
                InfoChip("${station.snr} dB", "SNR")
            }
        }
    }
}

@Composable
private fun InfoChip(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = TextPrimary,
            fontFamily = GeistMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        Text(
            text = label,
            color = TextFaint,
            fontSize = 9.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Bearing calculation
// ---------------------------------------------------------------------------

private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLam = Math.toRadians(lon2 - lon1)

    val y = sin(dLam) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLam)
    val bearing = Math.toDegrees(atan2(y, x))
    return (bearing + 360.0) % 360.0
}
