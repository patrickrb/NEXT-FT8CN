package radio.ks3ckc.ft8us.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
    bandLabel: String,
    frequencyMhz: String,
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

        // Right: frequency
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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

    Box(contentAlignment = Alignment.Center) {
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
