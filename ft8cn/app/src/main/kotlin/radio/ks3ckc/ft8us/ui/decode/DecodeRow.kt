package radio.ks3ckc.ft8us.ui.decode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bg7yoz.ft8cn.Ft8Message
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.maidenhead.MaidenheadGrid
import com.bg7yoz.ft8cn.timer.UtcTimer
import radio.ks3ckc.ft8us.theme.*
import radio.ks3ckc.ft8us.ui.components.QsoStatus
import radio.ks3ckc.ft8us.ui.components.SignalBar
import radio.ks3ckc.ft8us.ui.components.StatusPill

/**
 * A single decoded FT8 message row.
 *
 * Layout:
 *  - Left accent bar for CQ messages
 *  - "CQ" or "TO YOU" label
 *  - Callsign (large, monospace)
 *  - Grid locator
 *  - Status pill
 *  - Metadata row: signal bar, SNR, frequency, distance, UTC time
 *  - DX entity location line for CQ messages
 *
 * Background tinting:
 *  - Cyan glow when the message is directed at the operator
 *  - Surface tint for CQ messages
 *  - Transparent for others
 */
@Composable
fun DecodeRow(
    message: Ft8Message,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCQ = message.checkIsCQ()
    val isToMe = GeneralVariables.checkIsMyCallsign(message.callsignTo ?: "")
    val isWorked = message.isQSL_Callsign
    val shape = RoundedCornerShape(12.dp)

    // Background color based on message type
    val bgColor = when {
        isToMe -> Color(0x145CD6E8)   // cyan glow rgba(92,214,232,0.08)
        isCQ -> BgSurface              // surface card
        else -> Color.Transparent
    }
    val borderColor = when {
        isToMe -> Color(0x385CD6E8)   // rgba(92,214,232,0.22)
        isCQ -> Border
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(shape)
            .background(bgColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable { onClick() }
            .padding(start = 0.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Left accent bar for CQ messages
        if (isCQ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(52.dp)
                    .background(Accent, RoundedCornerShape(99.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
        } else {
            Spacer(modifier = Modifier.width(13.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            // Top row: label + callsign + grid + status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // CQ or "TO YOU" label
                if (isCQ) {
                    MessageLabel(text = "CQ", color = Accent, bgColor = AccentSoft)
                } else if (isToMe) {
                    MessageLabel(text = "\u2193 TO YOU", color = Signal, bgColor = SignalSoft)
                }

                // Callsign
                Text(
                    text = message.callsignFrom ?: "",
                    color = if (isToMe) Signal else TextPrimary,
                    fontFamily = GeistMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.02.sp,
                )

                // Grid locator
                val grid = message.maidenGrid ?: ""
                if (grid.isNotEmpty()) {
                    Text(
                        text = grid,
                        color = TextFaint,
                        fontFamily = GeistMonoFamily,
                        fontSize = 11.sp,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Status pill
                val status = resolveQsoStatus(message)
                StatusPill(status = status, compact = true)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata row: signal bar, SNR, frequency, distance, UTC time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SignalBar(snr = message.snr, width = 28.dp, height = 12.dp)

                MetaText("${message.snr} dB")
                MetaText("${message.getFreq_hz()} Hz")

                // Distance (computed from grid)
                val distanceText = computeDistanceText(message)
                if (distanceText.isNotEmpty()) {
                    MetaText(distanceText)
                }

                Spacer(modifier = Modifier.weight(1f))

                // UTC time
                MetaText(UtcTimer.getTimeHHMMSS(message.utcTime))
            }

            // DX entity location line for CQ messages
            val location = message.fromWhere
            if (isCQ && !location.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    radio.ks3ckc.ft8us.ui.components.FT8USIcons.Globe(
                        color = TextDim,
                        size = 12.dp,
                    )
                    Text(
                        text = location,
                        color = TextDim,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Small colored label chip (e.g., "CQ", "TO YOU").
 */
@Composable
private fun MessageLabel(
    text: String,
    color: Color,
    bgColor: Color,
) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text = text,
        modifier = Modifier
            .background(bgColor, shape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = color,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.sp,
    )
}

/**
 * Small metadata text used in the bottom info row.
 */
@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        color = TextFaint,
        fontFamily = GeistMonoFamily,
        fontSize = 10.5.sp,
        letterSpacing = 0.02.sp,
    )
}

/**
 * Resolve the [QsoStatus] for a given [Ft8Message] based on its state.
 */
internal fun resolveQsoStatus(message: Ft8Message): QsoStatus {
    val isCQ = message.checkIsCQ()
    val isWorked = message.isQSL_Callsign

    return when {
        isCQ && !isWorked -> QsoStatus.CQ
        isCQ && isWorked -> QsoStatus.WORKED
        GeneralVariables.checkIsMyCallsign(message.callsignTo ?: "") -> QsoStatus.PENDING
        isWorked -> QsoStatus.WORKED
        else -> QsoStatus.NEW
    }
}

/**
 * Compute a human-readable distance string between the operator's grid and the
 * message sender's grid, if both are available.
 */
private fun computeDistanceText(message: Ft8Message): String {
    val myGrid = GeneralVariables.getMyMaidenheadGrid()
    val theirGrid = message.maidenGrid
    if (myGrid.isNullOrEmpty() || theirGrid.isNullOrEmpty()) return ""
    return try {
        val dist = MaidenheadGrid.getDist(myGrid, theirGrid)
        if (dist > 0) "${String.format("%.0f", dist)} km" else ""
    } catch (_: Exception) {
        ""
    }
}
