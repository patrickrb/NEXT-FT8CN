package radio.ks3ckc.ft8us.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bg7yoz.ft8cn.Ft8Message
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.ft8transmit.FunctionOfTransmit
import radio.ks3ckc.ft8us.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Data class representing a single message in the QSO log.
 */
private data class QsoLogEntry(
    val direction: Direction,
    val utcTime: Long,
    val messageText: String,
    val snr: Int? = null,
) {
    enum class Direction { TX, RX }
}

/**
 * Collapsible panel showing the active QSO with live RX/TX message history
 * and TX message selector buttons.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ActiveQsoPanel(
    mainViewModel: MainViewModel,
    expanded: Boolean,
    onCollapse: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val toCallsign by mainViewModel.ft8TransmitSignal.mutableToCallsign.observeAsState()
    val functions by mainViewModel.ft8TransmitSignal.mutableFunctions.observeAsState(arrayListOf())
    val functionOrder by mainViewModel.ft8TransmitSignal.mutableFunctionOrder.observeAsState(6)
    val messageList by mainViewModel.mutableFt8MessageList.observeAsState(arrayListOf())
    val transmittingMessage by mainViewModel.ft8TransmitSignal.mutableTransmittingMessage.observeAsState("")

    val targetCallsign = toCallsign?.callsign ?: "CQ"
    val hasTarget = targetCallsign != "CQ" && targetCallsign.isNotEmpty()

    // Filter messages to/from the target station
    val qsoMessages: List<QsoLogEntry> = remember(messageList, messageList?.size, targetCallsign, transmittingMessage) {
        if (!hasTarget) return@remember emptyList()

        val entries = mutableListOf<QsoLogEntry>()

        // RX messages from the target station directed at us, or from us to the target
        val myCallsign = GeneralVariables.myCallsign ?: ""
        messageList?.forEach { msg ->
            val from = msg.callsignFrom ?: ""
            val to = msg.callsignTo ?: ""

            when {
                // RX: target station calling us
                from.equals(targetCallsign, ignoreCase = true) &&
                        (to.equals(myCallsign, ignoreCase = true) || GeneralVariables.checkIsMyCallsign(to)) -> {
                    entries.add(
                        QsoLogEntry(
                            direction = QsoLogEntry.Direction.RX,
                            utcTime = msg.utcTime,
                            messageText = msg.getMessageText() ?: "$from $to ${msg.extraInfo ?: ""}",
                            snr = msg.snr,
                        )
                    )
                }
                // TX: us calling the target station (messages from us in the decoded list)
                from.equals(myCallsign, ignoreCase = true) && to.equals(targetCallsign, ignoreCase = true) -> {
                    entries.add(
                        QsoLogEntry(
                            direction = QsoLogEntry.Direction.TX,
                            utcTime = msg.utcTime,
                            messageText = msg.getMessageText() ?: "$from $to ${msg.extraInfo ?: ""}",
                        )
                    )
                }
            }
        }
        entries.sortedBy { it.utcTime }
    }

    AnimatedVisibility(
        visible = expanded && hasTarget,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .drawBehind {
                    drawLine(
                        color = Border,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1f,
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Station header
            StationHeader(targetCallsign = targetCallsign, snr = toCallsign?.snr)

            Spacer(modifier = Modifier.height(6.dp))

            // Message log
            MessageLog(entries = qsoMessages)

            Spacer(modifier = Modifier.height(8.dp))

            // TX message selector
            TxSelector(
                functions = functions ?: arrayListOf(),
                currentOrder = functionOrder,
                onSelectOrder = { order ->
                    mainViewModel.ft8TransmitSignal.setCurrentFunctionOrder(order)
                    mainViewModel.ft8TransmitSignal.mutableFunctionOrder.postValue(order)
                },
            )
        }
    }
}

@Composable
private fun StationHeader(targetCallsign: String, snr: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "QSO",
                color = TextFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GeistMonoFamily,
                letterSpacing = 0.04.sp,
            )
            Text(
                text = targetCallsign,
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GeistMonoFamily,
            )
            if (snr != null) {
                Text(
                    text = if (snr >= 0) "+${snr}dB" else "${snr}dB",
                    color = Signal,
                    fontSize = 11.sp,
                    fontFamily = GeistMonoFamily,
                )
            }
        }
    }
}

@Composable
private fun MessageLog(entries: List<QsoLogEntry>) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Waiting for messages...",
                color = TextFaint,
                fontSize = 11.sp,
                fontFamily = GeistMonoFamily,
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 160.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(BgApp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(entries) { entry ->
                MessageLogRow(entry)
            }
        }
    }
}

@Composable
private fun MessageLogRow(entry: QsoLogEntry) {
    val utcFormat = remember {
        SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
    val timeStr = remember(entry.utcTime) {
        utcFormat.format(Date(entry.utcTime))
    }

    val isTx = entry.direction == QsoLogEntry.Direction.TX
    val dirColor = if (isTx) Accent else Signal
    val dirLabel = if (isTx) "TX" else "RX"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Direction badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(dirColor.copy(alpha = 0.15f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dirLabel,
                color = dirColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = GeistMonoFamily,
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // UTC time
        Text(
            text = timeStr,
            color = TextFaint,
            fontSize = 10.sp,
            fontFamily = GeistMonoFamily,
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Message text
        Text(
            text = entry.messageText,
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = GeistMonoFamily,
            modifier = Modifier.weight(1f),
        )

        // SNR for RX messages
        if (entry.snr != null && !isTx) {
            Text(
                text = if (entry.snr >= 0) "+${entry.snr}" else "${entry.snr}",
                color = Signal,
                fontSize = 10.sp,
                fontFamily = GeistMonoFamily,
            )
        }
    }
}

@Composable
private fun TxSelector(
    functions: ArrayList<FunctionOfTransmit>,
    currentOrder: Int,
    onSelectOrder: (Int) -> Unit,
) {
    if (functions.isEmpty()) return

    // Short labels for each function order
    val labels = mapOf(
        1 to "GRID",
        2 to "RPT",
        3 to "R-RPT",
        4 to "RR73",
        5 to "73",
        6 to "CQ",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "TX:",
            color = TextFaint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GeistMonoFamily,
            modifier = Modifier.align(Alignment.CenterVertically),
        )

        functions.forEach { func ->
            val order = func.functionOrder
            val isActive = order == currentOrder
            val isCompleted = func.isCompleted
            val label = labels[order] ?: "$order"

            val bgColor = when {
                isActive -> AccentSoft
                isCompleted -> SignalSoft
                else -> BgSurface3
            }
            val borderColor = when {
                isActive -> Accent.copy(alpha = 0.5f)
                else -> Color.Transparent
            }
            val textColor = when {
                isActive -> Accent
                isCompleted -> Signal
                else -> TextMuted
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .then(
                        if (isActive) Modifier.border(1.dp, borderColor, RoundedCornerShape(6.dp))
                        else Modifier
                    )
                    .clickable { onSelectOrder(order) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = GeistMonoFamily,
                    letterSpacing = 0.02.sp,
                )
            }
        }
    }
}

private val GeistMonoFamily = radio.ks3ckc.ft8us.theme.GeistMonoFamily
