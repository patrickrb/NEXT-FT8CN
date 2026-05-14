package radio.ks3ckc.ft8us.ui.decode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bg7yoz.ft8cn.Ft8Message
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.timer.UtcTimer
import radio.ks3ckc.ft8us.theme.*
import radio.ks3ckc.ft8us.ui.components.FilterChips
import radio.ks3ckc.ft8us.ui.components.TopBar
import radio.ks3ckc.ft8us.ui.components.TopBarSubtitle

/**
 * Main decode screen. Observes the ViewModel's LiveData for decoded FT8 messages,
 * shows a filter bar, and renders a scrolling list of [DecodeRow] items.
 * Tapping a row opens the [QsoSheet] bottom sheet with station details.
 */
@Composable
fun DecodeScreen(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    // Observe LiveData
    val messageList by mainViewModel.mutableFt8MessageList.observeAsState(arrayListOf())
    val decodedCount by mainViewModel.mutable_Decoded_Counter.observeAsState(0)
    val utcTime by mainViewModel.timerSec.observeAsState(0L)

    // Filter state
    val filterOptions = listOf("All", "CQ Calls", "New DXCC", "Needed", "For Me")
    var selectedFilter by rememberSaveable { mutableStateOf("All") }

    // Bottom sheet state
    var selectedMessage by remember { mutableStateOf<Ft8Message?>(null) }
    var sheetVisible by remember { mutableStateOf(false) }

    // Take a snapshot of the list for stable rendering (ArrayList is mutable)
    val messages: List<Ft8Message> = remember(messageList, messageList?.size) {
        ArrayList(messageList ?: arrayListOf())
    }

    // Apply filter
    val filteredMessages = remember(messages, selectedFilter) {
        filterMessages(messages, selectedFilter)
    }

    // Auto-scroll state
    val listState = rememberLazyListState()
    var previousCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.size > previousCount && filteredMessages.isNotEmpty()) {
            listState.animateScrollToItem(filteredMessages.size - 1)
        }
        previousCount = filteredMessages.size
    }

    // Format UTC time for the subtitle
    val utcString = if (utcTime > 0L) {
        UtcTimer.getTimeStr(utcTime)
    } else {
        "UTC : --:--:--"
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgApp),
        ) {
            // Top bar
            TopBar(
                title = "Decode",
                subtitle = {
                    TopBarSubtitle(
                        text = "$utcString  \u2022  $decodedCount decoded this cycle",
                    )
                },
            )

            // Filter chips
            FilterChips(
                options = filterOptions,
                selected = selectedFilter,
                onSelected = { selectedFilter = it },
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Message list or empty state
            if (filteredMessages.isEmpty()) {
                EmptyState(
                    selectedFilter = selectedFilter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(
                        items = filteredMessages,
                        key = { index, msg -> "${msg.utcTime}_${msg.callsignFrom}_${msg.freq_hz}_$index" },
                    ) { _, message ->
                        DecodeRow(
                            message = message,
                            onClick = {
                                selectedMessage = message
                                sheetVisible = true
                            },
                        )
                    }
                }
            }
        }

        // QSO bottom sheet (overlays on top)
        QsoSheet(
            message = selectedMessage,
            mainViewModel = mainViewModel,
            visible = sheetVisible,
            onDismiss = { sheetVisible = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Filter Logic
// ---------------------------------------------------------------------------

/**
 * Apply the selected filter to the message list.
 *
 * Filters:
 *  - All: no filtering
 *  - CQ Calls: only CQ messages
 *  - New DXCC: never-worked entity (!isQSL_Callsign on CQ calls)
 *  - Needed: need QSL confirmation (not in QSL callsign list)
 *  - For Me: callsignTo matches operator's callsign
 */
private fun filterMessages(
    messages: List<Ft8Message>,
    filter: String,
): List<Ft8Message> {
    return when (filter) {
        "CQ Calls" -> messages.filter { it.checkIsCQ() }
        "New DXCC" -> messages.filter { it.checkIsCQ() && !it.isQSL_Callsign }
        "Needed" -> messages.filter {
            !it.isQSL_Callsign &&
                !GeneralVariables.checkQSLCallsign(it.callsignFrom ?: "")
        }
        "For Me" -> messages.filter {
            GeneralVariables.checkIsMyCallsign(it.callsignTo ?: "")
        }
        else -> messages // "All"
    }
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState(
    selectedFilter: String,
    modifier: Modifier = Modifier,
) {
    val (title, subtitle) = when (selectedFilter) {
        "CQ Calls" -> "No CQ calls" to "No stations are calling CQ on this band right now."
        "New DXCC" -> "No new DXCC" to "No unworked DXCC entities have been decoded yet."
        "Needed" -> "Nothing needed" to "No stations needing confirmation found."
        "For Me" -> "No calls for you" to "No stations are calling your callsign right now."
        else -> "No signals decoded" to "Waiting for FT8 signals to appear..."
    }

    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = TextMuted,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = TextFaint,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}
