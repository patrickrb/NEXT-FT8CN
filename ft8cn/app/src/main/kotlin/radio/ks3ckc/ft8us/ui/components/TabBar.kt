package radio.ks3ckc.ft8us.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import radio.ks3ckc.ft8us.theme.*

enum class FT8USTab(val label: String) {
    DECODE("Decode"),
    MAP("Map"),
    WATERFALL("Waterfall"),
    LOG("Logbook"),
    SETTINGS("Settings"),
}

@Composable
fun TabBar(
    activeTab: FT8USTab,
    onTabSelected: (FT8USTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, BgApp.copy(alpha = 0.95f)),
                    startY = 0f,
                    endY = 40f,
                )
            )
            .padding(horizontal = 12.dp)
            .padding(top = 8.dp, bottom = 30.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (tab in FT8USTab.entries) {
            val isActive = tab == activeTab
            val color = if (isActive) Accent else TextFaint
            val strokeWidth = if (isActive) 1.8f else 1.5f

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onTabSelected(tab) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (tab) {
                    FT8USTab.DECODE -> FT8USIcons.Decode(color = color, strokeWidth = strokeWidth)
                    FT8USTab.MAP -> FT8USIcons.Globe(color = color, strokeWidth = strokeWidth)
                    FT8USTab.WATERFALL -> FT8USIcons.Waterfall(color = color, strokeWidth = strokeWidth)
                    FT8USTab.LOG -> FT8USIcons.Book(color = color, strokeWidth = strokeWidth)
                    FT8USTab.SETTINGS -> FT8USIcons.Cog(color = color, strokeWidth = strokeWidth)
                }
                Text(
                    text = tab.label,
                    color = color,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.02.sp,
                )
            }
        }
    }
}
