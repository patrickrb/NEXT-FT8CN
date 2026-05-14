package radio.ks3ckc.ft8us.ui.decode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bg7yoz.ft8cn.Ft8Message
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.maidenhead.MaidenheadGrid
import com.bg7yoz.ft8cn.rigs.BaseRigOperation
import com.bg7yoz.ft8cn.timer.UtcTimer
import radio.ks3ckc.ft8us.theme.*
import radio.ks3ckc.ft8us.ui.components.FT8USBottomSheet
import radio.ks3ckc.ft8us.ui.components.FT8USIcons
import radio.ks3ckc.ft8us.ui.components.GlassCard
import radio.ks3ckc.ft8us.ui.components.QsoStatus
import radio.ks3ckc.ft8us.ui.components.StatusPill

/**
 * Bottom sheet that displays full station details for a decoded message and
 * provides a "Call" action to initiate a QSO sequence.
 *
 * Sections:
 *  1. Station header: callsign avatar, callsign, status, location info
 *  2. Stat cards: Signal (SNR), Azimuth, Band
 *  3. QSO sequence visualizer (5 steps)
 *  4. "Call {callsign}" action button
 */
@Composable
fun QsoSheet(
    message: Ft8Message?,
    mainViewModel: MainViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FT8USBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        if (message != null) {
            QsoSheetContent(
                message = message,
                mainViewModel = mainViewModel,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun QsoSheetContent(
    message: Ft8Message,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val callsign = message.callsignFrom ?: ""
    val status = resolveQsoStatus(message)
    val isTransmitting by mainViewModel.ft8TransmitSignal.mutableIsTransmitting.observeAsState(false)
    val isActivated by mainViewModel.ft8TransmitSignal.mutableIsActivated.observeAsState(false)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // -- Station header --
        StationHeader(
            callsign = callsign,
            status = status,
            location = message.fromWhere,
            grid = message.maidenGrid,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // -- Stat cards: Signal, Azimuth, Band --
        StatCardsRow(message = message)

        Spacer(modifier = Modifier.height(20.dp))

        // -- QSO sequence visualizer --
        QsoSequenceVisualizer(
            callsign = callsign,
            message = message,
            isActivated = isActivated,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // -- Call button --
        val isQsoComplete = status == QsoStatus.WORKED || status == QsoStatus.CONFIRMED

        if (isQsoComplete) {
            // QSO complete banner
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x1A4ADE80))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FT8USIcons.Check(color = StatusConfirmed, size = 18.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "QSO Complete",
                        color = StatusConfirmed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            Button(
                onClick = {
                    mainViewModel.addFollowCallsign(callsign)
                    mainViewModel.ft8TransmitSignal.setActivated(true)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = BgApp,
                ),
                enabled = !isTransmitting,
            ) {
                FT8USIcons.Transmit(color = BgApp, size = 18.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Call $callsign",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Station Header
// ---------------------------------------------------------------------------

@Composable
private fun StationHeader(
    callsign: String,
    status: QsoStatus,
    location: String?,
    grid: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Callsign avatar circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(BgElev),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = callsign.take(2),
                color = Accent,
                fontFamily = GeistMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = callsign,
                    color = TextPrimary,
                    fontFamily = GeistMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                StatusPill(status = status, compact = true)
            }

            // Location info
            val locationParts = buildList {
                if (!location.isNullOrEmpty()) add(location)
                if (!grid.isNullOrEmpty()) add(grid)
            }
            if (locationParts.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FT8USIcons.Globe(color = TextFaint, size = 12.dp)
                    Text(
                        text = locationParts.joinToString(" \u2022 "),
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stat Cards
// ---------------------------------------------------------------------------

@Composable
private fun StatCardsRow(message: Ft8Message) {
    val myGrid = GeneralVariables.getMyMaidenheadGrid()
    val theirGrid = message.maidenGrid ?: ""

    // Compute azimuth
    val azimuthText = computeAzimuthText(myGrid, theirGrid)

    // Derive band label from message carrier frequency
    val bandLabel = try {
        BaseRigOperation.getFrequencyAllInfo(message.band)
    } catch (_: Exception) {
        "--"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatCard(
            label = "Signal",
            value = "${message.snr} dB",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Azimuth",
            value = azimuthText,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "Band",
            value = bandLabel,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                color = TextFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.06.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontFamily = GeistMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// QSO Sequence Visualizer
// ---------------------------------------------------------------------------

private data class QsoStep(
    val label: String,
    val txRxLabel: String,
    val messagePreview: String,
)

@Composable
private fun QsoSequenceVisualizer(
    callsign: String,
    message: Ft8Message,
    isActivated: Boolean,
) {
    val myCall = GeneralVariables.myCallsign ?: ""
    val grid = message.maidenGrid ?: ""
    val myGrid = GeneralVariables.getMyMaidenhead4Grid() ?: ""

    // Determine current QSO step based on extraInfo
    val currentFunOrder = GeneralVariables.checkFunOrder(message)

    val steps = listOf(
        QsoStep(
            label = "Send call",
            txRxLabel = "TX",
            messagePreview = "$callsign $myCall $myGrid",
        ),
        QsoStep(
            label = "Report sent",
            txRxLabel = "RX",
            messagePreview = "$myCall $callsign ${message.snr}",
        ),
        QsoStep(
            label = "Roger",
            txRxLabel = "TX",
            messagePreview = "$callsign $myCall R${message.snr}",
        ),
        QsoStep(
            label = "Confirm",
            txRxLabel = "RX",
            messagePreview = "$myCall $callsign RR73",
        ),
        QsoStep(
            label = "Logged",
            txRxLabel = "--",
            messagePreview = "$callsign $myCall 73",
        ),
    )

    // Map fun order to completed step count
    val completedSteps = when {
        currentFunOrder >= 5 -> 5  // 73 sent -> all done
        currentFunOrder == 4 -> 4  // RR73/RRR received
        currentFunOrder == 3 -> 3  // R-report sent
        currentFunOrder == 2 -> 2  // Report received
        currentFunOrder == 1 -> 1  // Grid / initial call
        isActivated -> 0           // Activated but not started
        else -> -1                 // Not started
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = "QSO SEQUENCE",
            color = TextFaint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.1.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        steps.forEachIndexed { index, step ->
            val isComplete = index < completedSteps
            val isCurrent = index == completedSteps

            QsoStepRow(
                stepNumber = index + 1,
                step = step,
                isComplete = isComplete,
                isCurrent = isCurrent,
                isLast = index == steps.lastIndex,
            )
        }
    }
}

@Composable
private fun QsoStepRow(
    stepNumber: Int,
    step: QsoStep,
    isComplete: Boolean,
    isCurrent: Boolean,
    isLast: Boolean,
) {
    val stepColor = when {
        isComplete -> StatusConfirmed
        isCurrent -> Accent
        else -> TextDim
    }
    val textColor = when {
        isComplete -> TextMuted
        isCurrent -> TextPrimary
        else -> TextDim
    }
    val txRxColor = when (step.txRxLabel) {
        "TX" -> StatusBad
        "RX" -> Signal
        else -> TextDim
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Step indicator (number or check)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isComplete) StatusConfirmed.copy(alpha = 0.15f)
                        else if (isCurrent) Accent.copy(alpha = 0.15f)
                        else BgSurface3
                    )
                    .border(
                        1.dp,
                        if (isComplete) StatusConfirmed.copy(alpha = 0.4f)
                        else if (isCurrent) Accent.copy(alpha = 0.4f)
                        else Border,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isComplete) {
                    FT8USIcons.Check(color = StatusConfirmed, size = 12.dp)
                } else {
                    Text(
                        text = "$stepNumber",
                        color = stepColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Connecting line between steps
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(if (isComplete) StatusConfirmed.copy(alpha = 0.3f) else Border)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = step.label,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                // TX/RX badge
                if (step.txRxLabel != "--") {
                    Text(
                        text = step.txRxLabel,
                        modifier = Modifier
                            .background(
                                txRxColor.copy(alpha = 0.12f),
                                RoundedCornerShape(3.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        color = txRxColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.06.sp,
                    )
                }
            }
            // Message preview
            Text(
                text = step.messagePreview,
                color = TextDim,
                fontFamily = GeistMonoFamily,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Compute the bearing/azimuth (in degrees) from the operator's grid to the
 * remote station's grid. Returns "--" if either grid is unavailable.
 */
private fun computeAzimuthText(myGrid: String?, theirGrid: String?): String {
    if (myGrid.isNullOrEmpty() || theirGrid.isNullOrEmpty()) return "--"
    return try {
        val myLatLng = MaidenheadGrid.gridToLatLng(myGrid) ?: return "--"
        val theirLatLng = MaidenheadGrid.gridToLatLng(theirGrid) ?: return "--"

        val lat1 = Math.toRadians(myLatLng.latitude)
        val lat2 = Math.toRadians(theirLatLng.latitude)
        val dLon = Math.toRadians(theirLatLng.longitude - myLatLng.longitude)

        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        val bearing = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
        "${String.format("%.0f", bearing)}\u00B0"
    } catch (_: Exception) {
        "--"
    }
}
