package radio.ks3ckc.ft8us.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.connector.ConnectMode
import com.bg7yoz.ft8cn.rigs.BaseRigOperation
import radio.ks3ckc.ft8us.theme.*
import radio.ks3ckc.ft8us.ui.components.GlassCard
import radio.ks3ckc.ft8us.ui.components.SettingsRow
import radio.ks3ckc.ft8us.ui.components.TopBar

/**
 * Settings screen that replaces the legacy ConfigFragment.
 * Reads state from [GeneralVariables] static fields and persists changes
 * via [MainViewModel.databaseOpr.writeConfig].
 */
@Composable
fun SettingsScreen(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    // Observe reactive fields from GeneralVariables
    val gridLive by GeneralVariables.mutableMyMaidenheadGrid.observeAsState(
        GeneralVariables.getMyMaidenheadGrid(),
    )
    val bandIndexLive by GeneralVariables.mutableBandChange.observeAsState(
        GeneralVariables.bandListIndex,
    )
    val baseFreqLive by GeneralVariables.mutableBaseFrequency.observeAsState(
        GeneralVariables.getBaseFrequency(),
    )

    // Local mutable state backed by GeneralVariables statics
    var synFrequency by remember { mutableStateOf(GeneralVariables.synFrequency) }
    var autoFollowCQ by remember { mutableStateOf(GeneralVariables.autoFollowCQ) }
    var autoCallFollow by remember { mutableStateOf(GeneralVariables.autoCallFollow) }
    var enableCloudlog by remember { mutableStateOf(GeneralVariables.enableCloudlog) }
    var enableQRZ by remember { mutableStateOf(GeneralVariables.enableQRZ) }
    var saveSWLMessage by remember { mutableStateOf(GeneralVariables.saveSWLMessage) }
    var saveSWL_QSO by remember { mutableStateOf(GeneralVariables.saveSWL_QSO) }

    // Derived display strings
    val callsign = GeneralVariables.myCallsign.orEmpty()
    val grid = gridLive.orEmpty()
    val connectModeStr = ConnectMode.getModeStr(GeneralVariables.connectMode)
    val bandStr = BaseRigOperation.getFrequencyAllInfo(GeneralVariables.band)
    val audioFreqStr = "${GeneralVariables.getBaseFrequencyStr()} Hz"
    val txDelayStr = "${GeneralVariables.transmitDelay} ms"
    val pttDelayStr = "${GeneralVariables.pttDelay} ms"
    val watchdogMinutes = GeneralVariables.launchSupervision / 60000
    val watchdogStr = if (watchdogMinutes == 0) "Off" else "$watchdogMinutes min"
    val rigConnected = mainViewModel.isRigConnected()
    val rigName = if (rigConnected) {
        mainViewModel.baseRig?.javaClass?.simpleName ?: "--"
    } else {
        "Not connected"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // -- Top bar --
        TopBar(title = "Settings")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // =====================================================================
            // 1. OPERATOR IDENTITY
            // =====================================================================
            SettingsSection(title = "OPERATOR IDENTITY") {
                OperatorCard(
                    callsign = callsign,
                    grid = grid,
                    rigName = rigName,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // =====================================================================
            // 2. RADIO
            // =====================================================================
            SettingsSection(title = "RADIO") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsRow(
                            label = "Connection Mode",
                            value = connectModeStr,
                            showChevron = true,
                            onClick = { /* open connection picker */ },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "Band & Frequency",
                            value = bandStr,
                            showChevron = true,
                            onClick = { /* open band selector */ },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "Audio Frequency",
                            value = audioFreqStr,
                            showChevron = true,
                            onClick = { /* open audio freq editor */ },
                        )
                    }
                }
            }

            // =====================================================================
            // 3. TRANSMISSION
            // =====================================================================
            SettingsSection(title = "TRANSMISSION") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsRow(
                            label = "TX/RX Split",
                            description = "Transmit on a different frequency than receive",
                            toggle = synFrequency,
                            onToggleChange = { checked ->
                                synFrequency = checked
                                GeneralVariables.synFrequency = checked
                                mainViewModel.databaseOpr.writeConfig(
                                    "synFreq", if (checked) "1" else "0", null,
                                )
                            },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "TX Watchdog",
                            description = "Auto-stop transmit after timeout",
                            value = watchdogStr,
                            showChevron = true,
                            onClick = { /* open watchdog editor */ },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "Stop After",
                            description = "Stop calling after N unanswered attempts",
                            value = if (GeneralVariables.noReplyLimit == 0) "Off"
                            else "${GeneralVariables.noReplyLimit} tries",
                            showChevron = true,
                            onClick = { /* open no-reply limit editor */ },
                        )
                    }
                }
            }

            // =====================================================================
            // 4. AUTO-SEQUENCE
            // =====================================================================
            SettingsSection(title = "AUTO-SEQUENCE") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsRow(
                            label = "Auto-call CQ",
                            description = "Automatically call stations calling CQ",
                            toggle = autoFollowCQ,
                            onToggleChange = { checked ->
                                autoFollowCQ = checked
                                GeneralVariables.autoFollowCQ = checked
                                mainViewModel.databaseOpr.writeConfig(
                                    "autoFollowCQ", if (checked) "1" else "0", null,
                                )
                            },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "Auto-call Followed",
                            description = "Continue calling followed callsigns automatically",
                            toggle = autoCallFollow,
                            onToggleChange = { checked ->
                                autoCallFollow = checked
                                GeneralVariables.autoCallFollow = checked
                                mainViewModel.databaseOpr.writeConfig(
                                    "autoCallFollow", if (checked) "1" else "0", null,
                                )
                            },
                        )
                    }
                }
            }

            // =====================================================================
            // 5. LOGGING & AWARDS
            // =====================================================================
            SettingsSection(title = "LOGGING & AWARDS") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsRow(
                            label = "Save SWL Decodes",
                            description = "Log all decoded messages to the database",
                            toggle = saveSWLMessage,
                            onToggleChange = { checked ->
                                saveSWLMessage = checked
                                GeneralVariables.saveSWLMessage = checked
                                mainViewModel.databaseOpr.writeConfig(
                                    "saveSWL", if (checked) "1" else "0", null,
                                )
                            },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "Save SWL QSOs",
                            description = "Log QSOs detected between other stations",
                            toggle = saveSWL_QSO,
                            onToggleChange = { checked ->
                                saveSWL_QSO = checked
                                GeneralVariables.saveSWL_QSO = checked
                                mainViewModel.databaseOpr.writeConfig(
                                    "saveSWLQSO", if (checked) "1" else "0", null,
                                )
                            },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "QRZ.com",
                            description = "Auto-upload QSOs to QRZ Logbook",
                            toggle = enableQRZ,
                            onToggleChange = { checked ->
                                enableQRZ = checked
                                GeneralVariables.enableQRZ = checked
                                mainViewModel.databaseOpr.writeConfig(
                                    "enableQRZ", if (checked) "1" else "0", null,
                                )
                            },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "Cloudlog",
                            description = "Auto-upload QSOs to a Cloudlog instance",
                            toggle = enableCloudlog,
                            onToggleChange = { checked ->
                                enableCloudlog = checked
                                GeneralVariables.enableCloudlog = checked
                                mainViewModel.databaseOpr.writeConfig(
                                    "enableCloudlog", if (checked) "1" else "0", null,
                                )
                            },
                        )
                    }
                }
            }

            // =====================================================================
            // 6. ADVANCED
            // =====================================================================
            SettingsSection(title = "ADVANCED") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsRow(
                            label = "PTT Delay",
                            description = "Delay after PTT before transmit audio begins",
                            value = pttDelayStr,
                            showChevron = true,
                            onClick = { /* open PTT delay editor */ },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "TX Delay",
                            description = "Delay before transmit to allow prior-cycle decode",
                            value = txDelayStr,
                            showChevron = true,
                            onClick = { /* open TX delay editor */ },
                        )
                    }
                }
            }

            // =====================================================================
            // 7. ABOUT
            // =====================================================================
            SettingsSection(title = "ABOUT") {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsRow(
                            label = "FT8US",
                            description = "Build ${GeneralVariables.BUILD_DATE}",
                            value = "v${GeneralVariables.VERSION}",
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "FAQ & Support",
                            showChevron = true,
                            onClick = { /* open support page */ },
                        )
                    }
                }
            }

            // Bottom spacer for scroll overscroll / nav bar inset
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * A settings section with an uppercase muted title and its content block.
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = TextFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

/**
 * Thin divider between rows inside a [GlassCard].
 */
@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = Border,
    )
}
