package radio.ks3ckc.ft8us

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.bluetooth.BluetoothStateBroadcastReceive
import com.bg7yoz.ft8cn.connector.CableSerialPort
import com.bg7yoz.ft8cn.connector.ConnectMode
import com.bg7yoz.ft8cn.callsign.CallsignDatabase
import com.bg7yoz.ft8cn.database.DatabaseOpr
import com.bg7yoz.ft8cn.database.OnAfterQueryConfig
import com.bg7yoz.ft8cn.database.OperationBand
import com.bg7yoz.ft8cn.log.ImportSharedLogs
import com.bg7yoz.ft8cn.log.OnShareLogEvents
import com.bg7yoz.ft8cn.maidenhead.MaidenheadGrid
import com.bg7yoz.ft8cn.ui.ToastMessage
import radio.ks3ckc.ft8us.theme.FT8USTheme
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ComposeMainActivity : ComponentActivity() {

    private var bluetoothReceiver: BluetoothStateBroadcastReceive? = null
    private lateinit var mainViewModel: MainViewModel

    companion object {
        private const val TAG = "ComposeMainActivity"
        private const val PERMISSION_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Build permissions list
        val permissions = buildPermissionsList()
        checkPermission(permissions)

        // Fullscreen and keep screen on
        @Suppress("DEPRECATION")
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        super.onCreate(savedInstanceState)

        GeneralVariables.getInstance().setMainContext(applicationContext)
        mainViewModel = MainViewModel.getInstance(this)
        ToastMessage.getInstance()

        // Register back press handler for exit confirmation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                android.app.AlertDialog.Builder(this@ComposeMainActivity)
                    .setMessage(getString(com.bg7yoz.ft8cn.R.string.exit_confirmation))
                    .setPositiveButton(getString(com.bg7yoz.ft8cn.R.string.exit)) { _, _ ->
                        mainViewModel.ft8TransmitSignal.isActivated = false
                        closeApp()
                    }
                    .setNegativeButton(getString(com.bg7yoz.ft8cn.R.string.cancel)) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .create()
                    .show()
            }
        })

        // Register Bluetooth state broadcast receiver
        registerBluetoothReceiver()
        if (mainViewModel.isBTConnected()) {
            mainViewModel.setBlueToothOn()
        }

        // Set Compose UI
        setContent {
            FT8USTheme {
                FT8USApp(mainViewModel)
            }
        }

        // Initialize data
        fileLog("=== APP START ===")
        initData()

        // Observe serial port changes for auto-connect (mirrors old MainActivity behavior)
        mainViewModel.mutableSerialPorts.observe(
            this,
            Observer { ports ->
                autoConnectUsbIfNeeded(ports)
            },
        )

        // Handle shared file import if needed
        if (mainViewModel.mutableImportShareRunning.value == true) {
            // Import is already running; UI will show progress
        } else {
            doReceiveShareFile(intent)
        }
    }

    private fun buildPermissionsList(): Array<String> {
        val base = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return base.toTypedArray()
    }

    private fun checkPermission(permissions: Array<String>) {
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Proceed regardless; same behavior as original
    }

    private fun initData() {
        if (mainViewModel.configIsLoaded) return

        if (mainViewModel.operationBand == null) {
            mainViewModel.operationBand = OperationBand.getInstance(baseContext)
        }

        mainViewModel.databaseOpr.getQslDxccToMap()

        mainViewModel.databaseOpr.getAllConfigParameter(object : OnAfterQueryConfig {
            override fun doOnBeforeQueryConfig(keyName: String?) {}

            override fun doOnAfterQueryConfig(keyName: String?, value: String?) {
                mainViewModel.configIsLoaded = true
                fileLog("configLoaded: instructionSet=${GeneralVariables.instructionSet}, " +
                    "baudRate=${GeneralVariables.baudRate}, " +
                    "controlMode=${GeneralVariables.controlMode}, " +
                    "connectMode=${GeneralVariables.connectMode}")
                val grid = MaidenheadGrid.getMyMaidenheadGrid(applicationContext)
                if (grid.isNotEmpty()) {
                    GeneralVariables.setMyMaidenheadGrid(grid)
                    mainViewModel.databaseOpr.writeConfig("grid", grid, null)
                }
                mainViewModel.ft8TransmitSignal.setTimer_sec(GeneralVariables.transmitDelay)

                // Scan for USB devices AFTER config is loaded
                fileLog("initData: scanning USB devices")
                mainViewModel.getUsbDevice()
                val ports = mainViewModel.mutableSerialPorts.value
                fileLog("initData: found ${ports?.size ?: 0} serial port(s)")
                mainViewModel.reinitializeAudioInput()

                // Delayed re-scan for slow USB enumeration
                Handler(Looper.getMainLooper()).postDelayed({
                    val connected = mainViewModel.isRigConnected()
                    fileLog("initData delayed: rigConnected=$connected")
                    if (!connected) {
                        mainViewModel.getUsbDevice()
                        val delayedPorts = mainViewModel.mutableSerialPorts.value
                        fileLog("initData delayed: found ${delayedPorts?.size ?: 0} serial port(s)")
                    }
                    mainViewModel.reinitializeAudioInput()
                }, 3000)
            }
        })

        DatabaseOpr.GetCallsignMapGrid(mainViewModel.databaseOpr.db).execute()
        mainViewModel.getFollowCallsignsFromDataBase()

        if (GeneralVariables.callsignDatabase == null) {
            GeneralVariables.callsignDatabase = CallsignDatabase.getInstance(baseContext, null, 1)
        }
    }

    private fun doReceiveShareFile(intent: Intent) {
        val uri: Uri? = intent.data
        if (uri != null) {
            try {
                val inputStream = baseContext.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Log.e(TAG, "Failed to open input stream for URI: $uri")
                    return
                }
                mainViewModel.mutableImportShareRunning.value = true
                val importSharedLogs = ImportSharedLogs(mainViewModel)
                Log.d(TAG, "Starting import...")
                importSharedLogs.doImport(
                    inputStream,
                    object : OnShareLogEvents {
                        override fun onPreparing(info: String?) {
                            mainViewModel.mutableShareInfo.postValue(info)
                        }

                        override fun onShareStart(count: Int, info: String?) {
                            mainViewModel.mutableSharePosition.postValue(0)
                            mainViewModel.mutableShareInfo.postValue(info)
                            mainViewModel.mutableImportShareRunning.postValue(true)
                            mainViewModel.mutableShareCount.postValue(count)
                        }

                        override fun onShareProgress(count: Int, position: Int, info: String?): Boolean {
                            mainViewModel.mutableSharePosition.postValue(position)
                            mainViewModel.mutableShareInfo.postValue(info)
                            mainViewModel.mutableShareCount.postValue(count)
                            return mainViewModel.mutableImportShareRunning.value == true
                        }

                        override fun afterGet(count: Int, info: String?) {
                            mainViewModel.mutableShareInfo.postValue(info)
                            mainViewModel.mutableImportShareRunning.postValue(false)
                        }

                        override fun onShareFailed(info: String?) {
                            mainViewModel.mutableShareInfo.postValue(info)
                        }
                    }
                )
            } catch (e: IOException) {
                mainViewModel.mutableImportShareRunning.postValue(false)
                Log.e(TAG, "Error: ${e.message}")
                ToastMessage.show(e.message)
            }
        }
    }

    // USB device attach events (singleTask launch mode)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED" == intent.action) {
            fileLog("onNewIntent: USB_DEVICE_ATTACHED")
            // Immediate scan
            mainViewModel.getUsbDevice()
            val ports = mainViewModel.mutableSerialPorts.value
            fileLog("onNewIntent: immediate scan found ${ports?.size ?: 0} port(s)")

            // Delayed re-scan and audio reinit (USB needs time to enumerate)
            Handler(Looper.getMainLooper()).postDelayed({
                val connected = mainViewModel.isRigConnected()
                fileLog("onNewIntent delayed: rigConnected=$connected")
                if (!connected) {
                    mainViewModel.getUsbDevice()
                    val delayedPorts = mainViewModel.mutableSerialPorts.value
                    fileLog("onNewIntent delayed: re-scan found ${delayedPorts?.size ?: 0} port(s)")
                }
                mainViewModel.reinitializeAudioInput()
            }, 2000)
        } else {
            setIntent(intent)
            doReceiveShareFile(intent)
        }
    }

    private fun registerBluetoothReceiver() {
        if (bluetoothReceiver == null) {
            bluetoothReceiver = BluetoothStateBroadcastReceive(applicationContext, mainViewModel)
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
    }

    private fun unregisterBluetoothReceiver() {
        bluetoothReceiver?.let {
            unregisterReceiver(it)
            bluetoothReceiver = null
        }
    }

    override fun onDestroy() {
        unregisterBluetoothReceiver()
        super.onDestroy()
    }

    /**
     * Auto-connect to USB serial rig when ports are detected, rig isn't already connected,
     * and connect mode is USB Cable with a non-VOX control mode.
     * Connects to the first detected port (most radios expose CAT as the first port).
     */
    private fun autoConnectUsbIfNeeded(ports: ArrayList<CableSerialPort.SerialPort>?) {
        if (ports.isNullOrEmpty()) {
            fileLog("autoConnect: no serial ports detected")
            return
        }
        if (mainViewModel.isRigConnected()) {
            fileLog("autoConnect: rig already connected, skipping")
            return
        }
        if (GeneralVariables.connectMode != ConnectMode.USB_CABLE) {
            fileLog("autoConnect: connectMode=${GeneralVariables.connectMode}, not USB_CABLE")
            return
        }
        if (!mainViewModel.configIsLoaded) {
            fileLog("autoConnect: config not loaded yet, skipping")
            return
        }

        fileLog("autoConnect: connecting to port 0 of ${ports.size} " +
            "(instructionSet=${GeneralVariables.instructionSet}, " +
            "baudRate=${GeneralVariables.baudRate}, " +
            "controlMode=${GeneralVariables.controlMode})")
        mainViewModel.connectCableRig(applicationContext, ports[0])
    }

    /** Write a line to /sdcard/Android/data/com.bg7yoz.ft8cn/files/debug.log */
    private fun fileLog(msg: String) {
        try {
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
            val dir = getExternalFilesDir(null) ?: return
            File(dir, "debug.log").appendText("$ts $msg\n")
        } catch (_: Exception) {}
        Log.d(TAG, msg)
    }

    private var volumeToast: android.widget.Toast? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val delta = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) 0.05f else -0.05f
                val newVol = (GeneralVariables.volumePercent + delta).coerceIn(0.0f, 1.0f)
                GeneralVariables.volumePercent = newVol
                GeneralVariables.mutableVolumePercent.postValue(newVol)
                val intVal = (newVol * 100).toInt()
                mainViewModel.databaseOpr.writeConfig("volumeValue", intVal.toString(), null)
                mainViewModel.baseRig?.connector?.setRFVolume(intVal)

                // Also adjust system music stream so audio is actually audible
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val systemVol = (newVol * maxVol).toInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, systemVol, 0)

                // Show volume toast
                volumeToast?.cancel()
                volumeToast = android.widget.Toast.makeText(this, "TX Volume: $intVal%", android.widget.Toast.LENGTH_SHORT)
                volumeToast?.show()

                return true
            }
            else -> return super.onKeyDown(keyCode, event)
        }
    }

    private fun closeApp() {
        mainViewModel.ft8TransmitSignal.isActivated = false
        mainViewModel.baseRig?.connector?.disconnect()
        mainViewModel.ft8SignalListener.stopListen()
        mainViewModel.hamRecorder?.stopRecord()
        mainViewModel.utcTimer?.delete()
        System.exit(0)
    }
}
