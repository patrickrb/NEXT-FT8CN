package radio.ks3ckc.ft8us.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import radio.ks3ckc.ft8us.theme.*

@Composable
fun TxStrip(
    isTransmitting: Boolean,
    isActivated: Boolean,
    bandLabel: String,
    frequencyMhz: String,
    onCallCQ: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (isTransmitting) {
        Brush.horizontalGradient(
            listOf(
                Color(0x1FFFAF5E),  // rgba(255,175,94,0.12)
                Color(0x0AFFAF5E),  // rgba(255,175,94,0.04)
            )
        )
    } else {
        Brush.horizontalGradient(listOf(BgSurface, BgSurface))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .drawBehind {
                // Top border
                drawLine(
                    color = Border,
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: status + band/mode
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PulseDot(color = if (isTransmitting) Accent else Signal)
            Text(
                text = if (isTransmitting) "TRANSMITTING" else "LISTENING",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GeistMonoFamily,
                letterSpacing = 0.02.sp,
            )
            Text("·", color = TextFaint, fontSize = 11.sp)
            Text(
                text = "$bandLabel FT8",
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = GeistMonoFamily,
                letterSpacing = 0.02.sp,
            )
        }

        // Right: CQ/Stop button + frequency
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // CQ / Stop pill button
            val buttonBg = if (isActivated) StatusBad.copy(alpha = 0.18f) else AccentSoft
            val buttonTextColor = if (isActivated) StatusBad else Accent
            val buttonLabel = if (isActivated) "STOP" else "CQ"

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(buttonBg)
                    .clickable { if (isActivated) onStop() else onCallCQ() }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = buttonLabel,
                    color = buttonTextColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GeistMonoFamily,
                    letterSpacing = 0.04.sp,
                )
            }

            Text(
                text = frequencyMhz,
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GeistMonoFamily,
            )
            Text(
                text = "MHz",
                color = TextFaint,
                fontSize = 11.sp,
                fontFamily = GeistMonoFamily,
            )
        }
    }
}

@Composable
private fun PulseDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseAlpha",
    )
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseSize",
    )

    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Pulse ring
        Box(
            modifier = Modifier
                .size((6 + pulseSize * 2).dp)
                .clip(CircleShape)
                .background(color.copy(alpha = pulseAlpha * 0.18f))
        )
        // Solid dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// Re-export the font families for use in TxStrip
private val GeistMonoFamily = radio.ks3ckc.ft8us.theme.GeistMonoFamily
