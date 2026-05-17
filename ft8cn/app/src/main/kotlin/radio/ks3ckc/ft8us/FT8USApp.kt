package radio.ks3ckc.ft8us

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.database.OperationBand
import radio.ks3ckc.ft8us.theme.BgApp
import radio.ks3ckc.ft8us.ui.components.ActiveQsoPanel
import radio.ks3ckc.ft8us.ui.components.FT8USTab
import radio.ks3ckc.ft8us.ui.components.TabBar
import radio.ks3ckc.ft8us.ui.components.TxStrip
import radio.ks3ckc.ft8us.ui.decode.DecodeScreen
import radio.ks3ckc.ft8us.ui.logbook.LogbookScreen
import radio.ks3ckc.ft8us.ui.map.MapScreen
import radio.ks3ckc.ft8us.ui.settings.SettingsScreen
import radio.ks3ckc.ft8us.ui.waterfall.WaterfallScreen

@Composable
fun FT8USApp(mainViewModel: MainViewModel) {
    val context = LocalContext.current
    var activeTab by rememberSaveable { mutableStateOf(FT8USTab.DECODE) }

    // Observe transmit state
    val isTransmitting by mainViewModel.ft8TransmitSignal.mutableIsTransmitting.observeAsState(false)
    val isActivated by mainViewModel.ft8TransmitSignal.mutableIsActivated.observeAsState(false)
    val txSlot by mainViewModel.ft8TransmitSignal.mutableSequential.observeAsState(mainViewModel.ft8TransmitSignal.sequential)

    // QSO panel expand/collapse state
    var qsoPanelExpanded by rememberSaveable { mutableStateOf(false) }

    // Auto-expand when activated, auto-collapse when deactivated
    LaunchedEffect(isActivated) {
        if (isActivated) {
            qsoPanelExpanded = true
        } else {
            qsoPanelExpanded = false
        }
    }

    // Derive band/frequency from GeneralVariables
    val bandIndex by GeneralVariables.mutableBandChange.observeAsState(GeneralVariables.bandListIndex)
    val bandLabel = if (bandIndex >= 0 && mainViewModel.operationBand != null) {
        try {
            OperationBand.getBandInfo(bandIndex)
        } catch (_: Exception) {
            GeneralVariables.getBandString()
        }
    } else {
        GeneralVariables.getBandString()
    }
    val baseFreq by GeneralVariables.mutableBaseFrequency.observeAsState(GeneralVariables.getBaseFrequency())
    val frequencyMhz = String.format("%.0f", baseFreq ?: GeneralVariables.getBaseFrequency())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgApp),
    ) {
        // Main content area (takes remaining space)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // Keep all screens in memory for real-time updates.
            // Only the active tab is shown; others remain composed but hidden.
            when (activeTab) {
                FT8USTab.DECODE -> DecodeScreen(mainViewModel)
                FT8USTab.MAP -> MapScreen(mainViewModel)
                FT8USTab.WATERFALL -> WaterfallScreen(mainViewModel)
                FT8USTab.LOG -> LogbookScreen(mainViewModel)
                FT8USTab.SETTINGS -> SettingsScreen(mainViewModel)
            }
        }

        // Active QSO panel — slides up above TxStrip when a QSO is in progress
        ActiveQsoPanel(
            mainViewModel = mainViewModel,
            expanded = qsoPanelExpanded,
            onCollapse = { qsoPanelExpanded = false },
        )

        // TX status strip — always visible above tab bar
        TxStrip(
            isTransmitting = isTransmitting,
            isActivated = isActivated,
            bandLabel = bandLabel,
            frequencyMhz = frequencyMhz,
            txSlot = txSlot,
            expanded = qsoPanelExpanded,
            onCallCQ = {
                if (GeneralVariables.myCallsign.isNullOrEmpty()) {
                    Toast.makeText(context, "Set your callsign in Settings before calling CQ", Toast.LENGTH_SHORT).show()
                } else {
                    mainViewModel.ft8TransmitSignal.resetToCQ()
                    mainViewModel.ft8TransmitSignal.setActivated(true)
                }
            },
            onStop = {
                mainViewModel.ft8TransmitSignal.setActivated(false)
            },
            onToggleSlot = {
                val current = mainViewModel.ft8TransmitSignal.sequential
                val newSlot = if (current == 0) 1 else 0
                mainViewModel.ft8TransmitSignal.sequential = newSlot
                mainViewModel.ft8TransmitSignal.mutableSequential.postValue(newSlot)
            },
            onToggleExpand = { qsoPanelExpanded = !qsoPanelExpanded },
        )

        // Bottom tab bar
        TabBar(
            activeTab = activeTab,
            onTabSelected = { activeTab = it },
        )
    }
}
