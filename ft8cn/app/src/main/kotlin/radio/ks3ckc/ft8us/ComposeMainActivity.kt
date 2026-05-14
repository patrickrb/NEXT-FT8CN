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
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bg7yoz.ft8cn.GeneralVariables
import com.bg7yoz.ft8cn.MainViewModel
import com.bg7yoz.ft8cn.bluetooth.BluetoothStateBroadcastReceive
import com.bg7yoz.ft8cn.callsign.CallsignDatabase
import com.bg7yoz.ft8cn.database.DatabaseOpr
import com.bg7yoz.ft8cn.database.OnAfterQueryConfig
import com.bg7yoz.ft8cn.database.OperationBand
import com.bg7yoz.ft8cn.log.ImportSharedLogs
import com.bg7yoz.ft8cn.log.OnShareLogEvents
import com.bg7yoz.ft8cn.maidenhead.MaidenheadGrid
import com.bg7yoz.ft8cn.ui.ToastMessage
import radio.ks3ckc.ft8us.theme.FT8USTheme
import java.io.IOException

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
        initData()

        // List USB devices
        mainViewModel.getUsbDevice()

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
                val grid = MaidenheadGrid.getMyMaidenheadGrid(applicationContext)
                if (grid.isNotEmpty()) {
                    GeneralVariables.setMyMaidenheadGrid(grid)
                    mainViewModel.databaseOpr.writeConfig("grid", grid, null)
                }
                mainViewModel.ft8TransmitSignal.setTimer_sec(GeneralVariables.transmitDelay)
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
                mainViewModel.mutableImportShareRunning.value = true
                val importSharedLogs = ImportSharedLogs(mainViewModel)
                Log.e(TAG, "Starting import...")
                importSharedLogs.doImport(
                    baseContext.contentResolver.openInputStream(uri),
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
            mainViewModel.getUsbDevice()
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

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Show exit confirmation
        android.app.AlertDialog.Builder(this)
            .setMessage("Are you sure you want to exit FT8US?")
            .setPositiveButton("Exit") { _, _ ->
                mainViewModel.ft8TransmitSignal.isActivated = false
                closeApp()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun closeApp() {
        mainViewModel.ft8TransmitSignal.isActivated = false
        mainViewModel.baseRig?.connector?.disconnect()
        mainViewModel.ft8SignalListener.stopListen()
        System.exit(0)
    }
}
