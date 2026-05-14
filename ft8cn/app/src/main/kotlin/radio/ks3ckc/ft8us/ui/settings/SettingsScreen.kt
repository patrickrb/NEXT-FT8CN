package radio.ks3ckc.ft8us.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bg7yoz.ft8cn.Ft8Message
import com.bg7yoz.ft8cn.log.ThirdPartyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.connector.ConnectMode
import com.bg7yoz.ft8cn.database.ControlMode
import com.bg7yoz.ft8cn.database.OperationBand
import com.bg7yoz.ft8cn.ft8signal.FT8Package
import com.bg7yoz.ft8cn.rigs.BaseRigOperation
import com.bg7yoz.ft8cn.rigs.InstructionSet
import com.bg7yoz.ft8cn.ui.LoginIcomRadioDialog
import com.bg7yoz.ft8cn.ui.SelectBluetoothDialog
import com.bg7yoz.ft8cn.ui.SelectFlexRadioDialog
import com.bg7yoz.ft8cn.ui.SelectXieguRadioDialog
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
    val context = LocalContext.current

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

    // Dialog visibility state
    var showEditOperator by remember { mutableStateOf(false) }
    var showConnectionMode by remember { mutableStateOf(false) }
    var showBandPicker by remember { mutableStateOf(false) }
    var showAudioFreq by remember { mutableStateOf(false) }
    var showWatchdog by remember { mutableStateOf(false) }
    var showStopAfter by remember { mutableStateOf(false) }
    var showPttDelay by remember { mutableStateOf(false) }
    var showTxDelay by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showCloudlog by remember { mutableStateOf(false) }

    // Operator identity edit state
    var callsignState by remember { mutableStateOf(GeneralVariables.myCallsign.orEmpty()) }
    var gridState by remember { mutableStateOf(GeneralVariables.getMyMaidenheadGrid().orEmpty()) }

    // Mutable state for settings that need to trigger recomposition on change
    var watchdogMs by remember { mutableIntStateOf(GeneralVariables.launchSupervision) }
    var noReplyLimit by remember { mutableIntStateOf(GeneralVariables.noReplyLimit) }
    var pttDelay by remember { mutableIntStateOf(GeneralVariables.pttDelay) }
    var txDelay by remember { mutableIntStateOf(GeneralVariables.transmitDelay) }
    var connectMode by remember { mutableIntStateOf(GeneralVariables.connectMode) }
    var cloudlogAddress by remember { mutableStateOf(GeneralVariables.cloudlogServerAddress.orEmpty()) }

    // Derived display strings
    val callsign = callsignState
    val grid = gridLive.orEmpty()
    val connectModeStr = ConnectMode.getModeStr(connectMode)
    val bandStr = BaseRigOperation.getFrequencyAllInfo(GeneralVariables.band)
    val audioFreqStr = "${GeneralVariables.getBaseFrequencyStr()} Hz"
    val txDelayStr = "$txDelay ms"
    val pttDelayStr = "$pttDelay ms"
    val watchdogMinutes = watchdogMs / 60000
    val watchdogStr = if (watchdogMinutes == 0) "Off" else "$watchdogMinutes min"
    val rigConnected = mainViewModel.isRigConnected()
    val rigName = if (rigConnected) {
        mainViewModel.baseRig?.javaClass?.simpleName ?: "--"
    } else {
        "Not connected"
    }
    val isCatMode = GeneralVariables.controlMode == ControlMode.CAT

    // =====================================================================
    // DIALOGS
    // =====================================================================

    // -- Edit Operator Dialog --
    if (showEditOperator) {
        EditOperatorDialog(
            initialCallsign = callsign,
            initialGrid = grid,
            onDismiss = { showEditOperator = false },
            onSave = { newCallsign, newGrid ->
                val trimmedCall = newCallsign.uppercase().trim()
                callsignState = trimmedCall
                GeneralVariables.myCallsign = trimmedCall
                mainViewModel.databaseOpr.writeConfig("callsign", trimmedCall, null)
                if (trimmedCall.isNotEmpty()) {
                    Ft8Message.hashList.addHash(FT8Package.getHash22(trimmedCall).toLong(), trimmedCall)
                    Ft8Message.hashList.addHash(FT8Package.getHash12(trimmedCall).toLong(), trimmedCall)
                    Ft8Message.hashList.addHash(FT8Package.getHash10(trimmedCall).toLong(), trimmedCall)
                }

                val formattedGrid = buildString {
                    newGrid.trim().forEachIndexed { i, c ->
                        append(if (i < 2) c.uppercaseChar() else c.lowercaseChar())
                    }
                }
                GeneralVariables.setMyMaidenheadGrid(formattedGrid)
                mainViewModel.databaseOpr.writeConfig("grid", formattedGrid, null)

                showEditOperator = false
            },
        )
    }

    // -- Connection Mode Picker --
    if (showConnectionMode) {
        val connectionOptions = listOf("USB Cable", "Bluetooth", "Network")
        val currentIndex = GeneralVariables.connectMode.coerceIn(0, 2)
        ListPickerDialog(
            title = "Connection Mode",
            items = connectionOptions,
            selectedIndex = currentIndex,
            onDismiss = { showConnectionMode = false },
            onSelect = { index ->
                showConnectionMode = false
                GeneralVariables.connectMode = index
                connectMode = index
                mainViewModel.databaseOpr.writeConfig("connectMode", index.toString(), null)
                when (index) {
                    ConnectMode.BLUE_TOOTH -> {
                        SelectBluetoothDialog(context, mainViewModel).show()
                    }
                    ConnectMode.NETWORK -> {
                        when (GeneralVariables.instructionSet) {
                            InstructionSet.FLEX_NETWORK ->
                                SelectFlexRadioDialog(context, mainViewModel).show()
                            InstructionSet.XIEGU_6100_FT8CNS ->
                                SelectXieguRadioDialog(context, mainViewModel).show()
                            else ->
                                LoginIcomRadioDialog(context, mainViewModel).show()
                        }
                    }
                    // USB Cable: mode is set, no further dialog needed
                }
            },
        )
    }

    // -- Band & Frequency Picker --
    if (showBandPicker) {
        val bandItems = (0 until OperationBand.bandList.size).map { i ->
            OperationBand.getBandInfo(i)
        }
        val currentBandIndex = GeneralVariables.bandListIndex.coerceAtLeast(0)
        ListPickerDialog(
            title = "Band & Frequency",
            items = bandItems,
            selectedIndex = currentBandIndex,
            onDismiss = { showBandPicker = false },
            onSelect = { index ->
                showBandPicker = false
                GeneralVariables.bandListIndex = index
                GeneralVariables.band = OperationBand.getBandFreq(index)
                mainViewModel.databaseOpr.writeConfig(
                    "bandFreq", GeneralVariables.band.toString(), null,
                )
                mainViewModel.databaseOpr.getAllQSLCallsigns()
                if (GeneralVariables.controlMode == ControlMode.CAT
                    || GeneralVariables.controlMode == ControlMode.RTS
                    || GeneralVariables.controlMode == ControlMode.DTR
                ) {
                    mainViewModel.setOperationBand()
                }
            },
        )
    }

    // -- Audio Frequency Editor --
    if (showAudioFreq) {
        NumberInputDialog(
            title = "Audio Frequency",
            suffix = "Hz",
            initialValue = GeneralVariables.getBaseFrequency().toInt(),
            min = 100,
            max = 2900,
            onDismiss = { showAudioFreq = false },
            onSave = { value ->
                showAudioFreq = false
                val clamped = value.toFloat().coerceIn(100f, 2900f)
                GeneralVariables.setBaseFrequency(clamped)
                mainViewModel.databaseOpr.writeConfig("freq", clamped.toInt().toString(), null)
            },
        )
    }

    // -- TX Watchdog Picker --
    if (showWatchdog) {
        // Build the same options as LaunchSupervisionSpinnerAdapter:
        // index 0 = Off (0 ms), index 1..10 = (index*10-5) minutes
        val watchdogOptions = mutableListOf("Off")
        for (i in 1..10) {
            watchdogOptions.add("${i * 10 - 5} min")
        }
        // Find current selection index from stored ms value
        val currentWatchdogIndex = if (watchdogMs == 0) {
            0
        } else {
            ((watchdogMs - 5 * 60 * 1000) / 60 / 1000 / 10).coerceIn(0, 10)
        }
        ListPickerDialog(
            title = "TX Watchdog",
            items = watchdogOptions,
            selectedIndex = currentWatchdogIndex,
            onDismiss = { showWatchdog = false },
            onSelect = { index ->
                showWatchdog = false
                // Same formula as LaunchSupervisionSpinnerAdapter.getTimeOut()
                val ms = if (index == 0) 0 else (index * 10 - 5) * 60 * 1000
                GeneralVariables.launchSupervision = ms
                watchdogMs = ms
                mainViewModel.databaseOpr.writeConfig(
                    "launchSupervision", ms.toString(), null,
                )
            },
        )
    }

    // -- Stop After (No Reply Limit) Picker --
    if (showStopAfter) {
        val stopAfterOptions = mutableListOf("Off")
        for (i in 1..30) {
            stopAfterOptions.add("$i tries")
        }
        ListPickerDialog(
            title = "Stop After",
            items = stopAfterOptions,
            selectedIndex = noReplyLimit.coerceIn(0, 30),
            onDismiss = { showStopAfter = false },
            onSelect = { index ->
                showStopAfter = false
                GeneralVariables.noReplyLimit = index
                noReplyLimit = index
                mainViewModel.databaseOpr.writeConfig(
                    "noReplyLimit", index.toString(), null,
                )
            },
        )
    }

    // -- PTT Delay Picker --
    if (showPttDelay) {
        val pttDelayOptions = (0 until 20).map { "${it * 10} ms" }
        val currentPttIndex = (pttDelay / 10).coerceIn(0, 19)
        ListPickerDialog(
            title = "PTT Delay",
            items = pttDelayOptions,
            selectedIndex = currentPttIndex,
            onDismiss = { showPttDelay = false },
            onSelect = { index ->
                showPttDelay = false
                val ms = index * 10
                GeneralVariables.pttDelay = ms
                pttDelay = ms
                mainViewModel.databaseOpr.writeConfig("pttDelay", ms.toString(), null)
            },
        )
    }

    // -- TX Delay Editor --
    if (showTxDelay) {
        NumberInputDialog(
            title = "TX Delay",
            suffix = "ms",
            initialValue = txDelay,
            min = 1,
            max = 9999,
            onDismiss = { showTxDelay = false },
            onSave = { value ->
                showTxDelay = false
                val clamped = value.coerceIn(1, 9999)
                GeneralVariables.transmitDelay = clamped
                txDelay = clamped
                mainViewModel.ft8TransmitSignal.setTimer_sec(clamped)
                mainViewModel.databaseOpr.writeConfig("transDelay", clamped.toString(), null)
            },
        )
    }

    // -- About / FAQ Dialog --
    if (showAbout) {
        InfoDialog(
            title = "FT8US",
            body = "Version ${GeneralVariables.VERSION}\n" +
                "Build ${GeneralVariables.BUILD_DATE}\n\n" +
                "Based on FT8CN by BG7YOZ\n\n" +
                "FT8US is a standalone FT8 transceiver app for Android. " +
                "It supports USB, Bluetooth, and network rig control " +
                "with automatic sequencing and logging.",
            onDismiss = { showAbout = false },
        )
    }

    // -- Cloudlog Settings Dialog --
    if (showCloudlog) {
        CloudlogSettingsDialog(
            initialAddress = GeneralVariables.cloudlogServerAddress.orEmpty(),
            initialApiKey = GeneralVariables.cloudlogApiKey.orEmpty(),
            initialStationId = GeneralVariables.cloudlogStationID.orEmpty(),
            onDismiss = { showCloudlog = false },
            onSave = { address, apiKey, stationId ->
                GeneralVariables.cloudlogServerAddress = address
                GeneralVariables.cloudlogApiKey = apiKey
                GeneralVariables.cloudlogStationID = stationId
                cloudlogAddress = address
                mainViewModel.databaseOpr.writeConfig("cloudlogServerAddress", address, null)
                mainViewModel.databaseOpr.writeConfig("cloudlogApiKey", apiKey, null)
                mainViewModel.databaseOpr.writeConfig("cloudlogStationID", stationId, null)
                showCloudlog = false
            },
        )
    }

    // =====================================================================
    // SCREEN CONTENT
    // =====================================================================

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
                    onClick = { showEditOperator = true },
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
                            showChevron = isCatMode,
                            onClick = if (isCatMode) {{ showConnectionMode = true }} else null,
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "Band & Frequency",
                            value = bandStr,
                            showChevron = true,
                            onClick = { showBandPicker = true },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "Audio Frequency",
                            value = audioFreqStr,
                            showChevron = !synFrequency,
                            onClick = if (!synFrequency) {{ showAudioFreq = true }} else null,
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
                            onClick = { showWatchdog = true },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "Stop After",
                            description = "Stop calling after N unanswered attempts",
                            value = if (noReplyLimit == 0) "Off"
                            else "$noReplyLimit tries",
                            showChevron = true,
                            onClick = { showStopAfter = true },
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
                            value = cloudlogAddress.ifEmpty { "Not configured" },
                            toggle = enableCloudlog,
                            onToggleChange = { checked ->
                                enableCloudlog = checked
                                GeneralVariables.enableCloudlog = checked
                                mainViewModel.databaseOpr.writeConfig(
                                    "enableCloudlog", if (checked) "1" else "0", null,
                                )
                            },
                            showChevron = true,
                            onClick = { showCloudlog = true },
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
                            onClick = { showPttDelay = true },
                        )
                        SectionDivider()
                        SettingsRow(
                            label = "TX Delay",
                            description = "Delay before transmit to allow prior-cycle decode",
                            value = txDelayStr,
                            showChevron = true,
                            onClick = { showTxDelay = true },
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
                            onClick = { showAbout = true },
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

// ---------------------------------------------------------------------------
// Reusable dialog composables
// ---------------------------------------------------------------------------

/**
 * Dialog for editing callsign and grid locator.
 */
@Composable
private fun EditOperatorDialog(
    initialCallsign: String,
    initialGrid: String,
    onDismiss: () -> Unit,
    onSave: (callsign: String, grid: String) -> Unit,
) {
    var callsignInput by remember { mutableStateOf(TextFieldValue(initialCallsign)) }
    var gridInput by remember { mutableStateOf(TextFieldValue(initialGrid)) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = Accent,
        focusedBorderColor = Accent,
        unfocusedBorderColor = BorderStrong,
        focusedLabelColor = Accent,
        unfocusedLabelColor = TextMuted,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgSurface2)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Edit Operator Identity",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )

            OutlinedTextField(
                value = callsignInput,
                onValueChange = { callsignInput = it },
                label = { Text("Callsign") },
                placeholder = { Text("e.g. W1AW", color = TextFaint) },
                singleLine = true,
                colors = fieldColors,
                textStyle = TextStyle(
                    fontFamily = GeistMonoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = gridInput,
                onValueChange = { gridInput = it },
                label = { Text("Grid Locator") },
                placeholder = { Text("e.g. FN31pr", color = TextFaint) },
                singleLine = true,
                colors = fieldColors,
                textStyle = TextStyle(
                    fontFamily = GeistMonoFamily,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
                TextButton(
                    onClick = { onSave(callsignInput.text, gridInput.text) },
                ) {
                    Text("Save", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Dialog for configuring Cloudlog server address, API key, and station ID.
 * Includes a Test Connection button that calls [ThirdPartyService.CheckCloudlogConnection].
 */
@Composable
private fun CloudlogSettingsDialog(
    initialAddress: String,
    initialApiKey: String,
    initialStationId: String,
    onDismiss: () -> Unit,
    onSave: (address: String, apiKey: String, stationId: String) -> Unit,
) {
    var addressInput by remember { mutableStateOf(TextFieldValue(initialAddress)) }
    var apiKeyInput by remember { mutableStateOf(TextFieldValue(initialApiKey)) }
    var stationIdInput by remember { mutableStateOf(TextFieldValue(initialStationId)) }

    // Test connection state: null = idle, true = pass, false = fail
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = Accent,
        focusedBorderColor = Accent,
        unfocusedBorderColor = BorderStrong,
        focusedLabelColor = Accent,
        unfocusedLabelColor = TextMuted,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgSurface2)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Cloudlog Settings",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )

            OutlinedTextField(
                value = addressInput,
                onValueChange = {
                    addressInput = it
                    testResult = null
                },
                label = { Text("Server Address") },
                placeholder = { Text("https://log.example.com/", color = TextFaint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors,
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = {
                    apiKeyInput = it
                    testResult = null
                },
                label = { Text("API Key") },
                placeholder = { Text("Your Cloudlog API key", color = TextFaint) },
                singleLine = true,
                colors = fieldColors,
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = stationIdInput,
                onValueChange = {
                    stationIdInput = it
                    testResult = null
                },
                label = { Text("Station ID") },
                placeholder = { Text("e.g. 1", color = TextFaint) },
                singleLine = true,
                colors = fieldColors,
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth(),
            )

            // Test Connection button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        // Write current input values to GeneralVariables so the test uses them
                        GeneralVariables.cloudlogServerAddress = addressInput.text
                        GeneralVariables.cloudlogApiKey = apiKeyInput.text
                        GeneralVariables.cloudlogStationID = stationIdInput.text
                        isTesting = true
                        testResult = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                ThirdPartyService.CheckCloudlogConnection()
                            }
                            testResult = result
                            isTesting = false
                        }
                    },
                    enabled = !isTesting,
                ) {
                    Text(
                        text = "Test Connection",
                        color = if (isTesting) TextFaint else Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(16.dp)
                            .height(16.dp),
                        strokeWidth = 2.dp,
                        color = Accent,
                    )
                }
                if (testResult != null) {
                    Text(
                        text = if (testResult == true) "Pass" else "Fail",
                        color = if (testResult == true) StatusConfirmed else StatusBad,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
                TextButton(
                    onClick = {
                        onSave(
                            addressInput.text.trim(),
                            apiKeyInput.text.trim(),
                            stationIdInput.text.trim(),
                        )
                    },
                ) {
                    Text("Save", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Scrollable list picker dialog with highlighted current selection.
 */
@Composable
private fun ListPickerDialog(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (index: Int) -> Unit,
) {
    val listState = rememberLazyListState()

    // Scroll to the selected item when the dialog opens
    LaunchedEffect(selectedIndex) {
        if (selectedIndex > 0) {
            listState.scrollToItem((selectedIndex - 2).coerceAtLeast(0))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgSurface2)
                .padding(vertical = 24.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 0.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                itemsIndexed(items) { index, item ->
                    val isSelected = index == selectedIndex
                    val bg = if (isSelected) AccentSoft else BgSurface2
                    val textColor = if (isSelected) Accent else TextPrimary

                    Text(
                        text = item,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .background(bg)
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
            }
        }
    }
}

/**
 * Numeric text input dialog with min/max validation.
 */
@Composable
private fun NumberInputDialog(
    title: String,
    suffix: String,
    initialValue: Int,
    min: Int,
    max: Int,
    onDismiss: () -> Unit,
    onSave: (value: Int) -> Unit,
) {
    var textInput by remember { mutableStateOf(TextFieldValue(initialValue.toString())) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = Accent,
        focusedBorderColor = Accent,
        unfocusedBorderColor = BorderStrong,
        focusedLabelColor = Accent,
        unfocusedLabelColor = TextMuted,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgSurface2)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )

            OutlinedTextField(
                value = textInput,
                onValueChange = { newValue ->
                    // Only allow digits
                    if (newValue.text.all { it.isDigit() }) {
                        textInput = newValue
                    }
                },
                label = { Text("$min\u2013$max $suffix") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
                textStyle = TextStyle(
                    fontFamily = GeistMonoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextMuted)
                }
                TextButton(
                    onClick = {
                        val parsed = textInput.text.toIntOrNull() ?: initialValue
                        onSave(parsed.coerceIn(min, max))
                    },
                ) {
                    Text("Save", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Simple informational dialog with a dismiss button.
 */
@Composable
private fun InfoDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgSurface2)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )

            Text(
                text = body,
                color = TextMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("OK", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
