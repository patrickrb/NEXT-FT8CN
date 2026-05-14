package com.bg7yoz.ft8cn.connector;
/**
 * Class for USB serial port operations. USB serial drivers are in the serialport directory,
 * mainly CDC, CH34x, CP21xx, FTDI, etc.
 *
 * @author BGY70Z
 * @date 2023-03-20
 */

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Log;

import com.bg7yoz.ft8cn.BuildConfig;
import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.serialport.CdcAcmSerialDriver;
import com.bg7yoz.ft8cn.serialport.UsbSerialDriver;
import com.bg7yoz.ft8cn.serialport.UsbSerialPort;
import com.bg7yoz.ft8cn.serialport.UsbSerialProber;
import com.bg7yoz.ft8cn.serialport.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;


public class CableSerialPort {
    private static final String TAG = "CableSerialPort";
    private OnConnectorStateChanged onStateChanged;
    public static final int SEND_TIMEOUT = 2000;

    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID;

    public enum UsbPermission {Unknown, Requested, Granted, Denied}

    private UsbPermission usbPermission = UsbPermission.Unknown;

    private BroadcastReceiver broadcastReceiver;
    private final Context context;

    private int vendorId = 0x0c26;//Device ID
    private int portNum = 0;//Port number
    private int baudRate = 19200;//Baud rate

    private UsbSerialPort usbSerialPort;
    private SerialInputOutputManager usbIoManager;
    public SerialInputOutputManager.Listener ioListener = null;

    private UsbManager usbManager;
    private UsbDeviceConnection usbConnection;
    private UsbSerialDriver driver;


    private boolean connected = false;//Whether currently connected

    public CableSerialPort(Context mContext, SerialPort serialPort, int baud, OnConnectorStateChanged connectorStateChanged) {
        vendorId = serialPort.vendorId;
        portNum = serialPort.portNum;
        baudRate = baud;
        context = mContext;
        this.onStateChanged=connectorStateChanged;
        doBroadcast();
    }

    public CableSerialPort(Context mContext) {
        context = mContext;
        doBroadcast();
    }

    private void doBroadcast() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    usbPermission = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            ? UsbPermission.Granted : UsbPermission.Denied;
                    connect();
                }
            }
        };
    }

    private boolean prepare() {
        registerRigSerialPort(context);
        UsbDevice device = null;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        //Set connection to null here so we can check permissions by null status later.
        usbConnection = null;
        //Should we do a permission check here?
        if (usbManager == null) {
            return false;
        }


        for (UsbDevice v : usbManager.getDeviceList().values()) {
            if (v.getVendorId() == vendorId) {
                device = v;
            }
        }
        if (device == null) {
            Log.e(TAG, String.format("Failed to open serial device: device 0x%04x not found", vendorId));
            return false;
        }
        driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            //Try adding the unknown device to the CDC driver
            driver = new CdcAcmSerialDriver(device);
        }
        if (driver.getPorts().size() < portNum) {
            Log.e(TAG, "Serial port number does not exist, cannot open.");
            return false;
        }
        Log.d(TAG, "connect: port size:" + String.valueOf(driver.getPorts().size()));
        usbSerialPort = driver.getPorts().get(portNum);
        usbConnection = usbManager.openDevice(driver.getDevice());

        return true;

    }

    @SuppressLint("UnspecifiedImmutableFlag")
    public boolean connect() {
        connected = false;
        if (!prepare()) {
            //return false;
        }
        if (driver == null) {
            if (onStateChanged!=null){
                onStateChanged.onRunError(GeneralVariables.getStringFromResource(R.string.serial_no_driver));
            }
            return false;
        }
        if (usbConnection == null && usbPermission == UsbPermission.Unknown
                && !usbManager.hasPermission(driver.getDevice())) {
            usbPermission = UsbPermission.Requested;

            PendingIntent usbPermissionIntent;

            //Starting from Android 12, PendingIntent requires explicit mutability flag.
            //FLAG_MUTABLE is needed so the system can attach EXTRA_PERMISSION_GRANTED.
            //Intent must be explicit (set package) to avoid MutableImplicitPendingIntent crash.
            Intent permIntent = new Intent(INTENT_ACTION_GRANT_USB);
            permIntent.setPackage(context.getPackageName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                usbPermissionIntent = PendingIntent.getBroadcast(context, 0
                        , permIntent, PendingIntent.FLAG_MUTABLE);
            } else {
                usbPermissionIntent = PendingIntent.getBroadcast(context, 0
                        , permIntent, 0);
            }


            //PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(context, 0
            //        , new Intent(INTENT_ACTION_GRANT_USB), PendingIntent.FLAG_MUTABLE);
            // , new Intent(INTENT_ACTION_GRANT_USB), 0);


            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            prepare();
        }
        if (usbConnection == null) {
            if (onStateChanged!=null){
                onStateChanged.onRunError(GeneralVariables.getStringFromResource(R.string.serial_connect_no_access));
            }

            return false;
        }
        try {
            usbSerialPort.open(usbConnection);
            //Baud rate, stop bits
            //usbSerialPort.setParameters(baudRate, 8, 1, UsbSerialPort.PARITY_NONE);
            Log.d(TAG,String.format("serial:baud rate：%d,data bits:%d,stop bits:%d,parity bit:%d"
                    ,baudRate,GeneralVariables.serialDataBits
                    ,GeneralVariables.serialStopBits
                    ,GeneralVariables.serialParity));
            usbSerialPort.setParameters(baudRate, GeneralVariables.serialDataBits
                    , GeneralVariables.serialStopBits, GeneralVariables.serialParity);
            usbIoManager = new SerialInputOutputManager(usbSerialPort, new SerialInputOutputManager.Listener() {
                @Override
                public void onNewData(byte[] data) {
                    if (ioListener != null) {
                        ioListener.onNewData(data);
                    }
                }

                @Override
                public void onRunError(Exception e) {
                    if (ioListener != null) {
                        ioListener.onRunError(e);
                    }
                    disconnect();
                }
            });
            usbIoManager.start();
            Log.d(TAG, "Serial port opened successfully!");
            connected = true;

            if (onStateChanged!=null){
                onStateChanged.onConnected();
            }


        } catch (Exception e) {
            Log.e(TAG, "Failed to open serial port: " + e.getMessage());
            if (onStateChanged!=null){
                onStateChanged.onRunError(GeneralVariables.getStringFromResource(R.string.serial_connect_failed)
                        + e.getMessage());
            }
            disconnect();
            return false;
        }
        return true;
    }

    public boolean sendData(final byte[] src) {
        if (usbSerialPort != null) {
            try {
                usbSerialPort.write(src, SEND_TIMEOUT);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error sending data: " + e.getMessage());
                return false;
            }
            return true;
        } else {
            Log.e(TAG, "Cannot send data, serial port is not open.");
            return false;
        }

    }

    public void disconnect() {
        connected = false;
        if (onStateChanged!=null){
            onStateChanged.onDisconnected();
        }
        if (usbIoManager != null) {
            usbIoManager.setListener(null);
            usbIoManager.stop();
        }
        usbIoManager = null;
        try {
            if (usbSerialPort != null) {
                usbSerialPort.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Error closing serial port: " + e.getMessage());
        }
        usbSerialPort = null;
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (IllegalArgumentException e) {
            // Already unregistered
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public void registerRigSerialPort(Context context) {
        Log.d(TAG, "registerRigSerialPort: registered!");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION_GRANT_USB));
        }
    }

    public void unregisterRigSerialPort(Activity activity) {
        Log.d(TAG, "unregisterRigSerialPort: unregistered!");
        activity.unregisterReceiver(broadcastReceiver);
    }


    /**
     * Toggle RTS on and off
     *
     * @param rts_on true: on, false: off
     */
    public void setRTS_On(boolean rts_on) {
        try {
            EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
            if (controlLines.contains(UsbSerialPort.ControlLine.RTS)) {
                usbSerialPort.setRTS(rts_on);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setDTR_On(boolean dtr_on) {
        try {
            EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
            if (controlLines.contains(UsbSerialPort.ControlLine.DTR)) {
                usbSerialPort.setDTR(dtr_on);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "setDTR_On: " + e.getMessage());
        }
    }

    public OnConnectorStateChanged getOnStateChanged() {
        return onStateChanged;
    }

    public void setOnStateChanged(OnConnectorStateChanged onStateChanged) {
        this.onStateChanged = onStateChanged;
    }

    public int getVendorId() {
        return vendorId;
    }

    public void setVendorId(int deviceId) {
        this.vendorId = deviceId;
    }

    public int getPortNum() {
        return portNum;
    }

    public void setPortNum(int portNum) {
        this.portNum = portNum;
    }

    public int getBaudRate() {
        return baudRate;
    }

    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
    }

    /**
     * Get the list of available serial port devices on this device
     *
     * @param context context
     * @return list of serial port devices
     */
    public static ArrayList<SerialPort> listSerialPorts(Context context) {
        ArrayList<SerialPort> serialPorts = new ArrayList<>();
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return serialPorts;

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
            if (driver == null) {
                continue;
                //Try adding the unknown device to the CDC driver
                //driver = new CdcAcmSerialDriver(device);
            }
            for (int i = 0; i < driver.getPorts().size(); i++) {
                serialPorts.add(new SerialPort(device.getDeviceId(), device.getVendorId()
                        , device.getProductId(), i));
            }
        }
        return serialPorts;
    }

    public boolean isConnected() {
        return connected;
    }


    public static class SerialPort {
        public int deviceId = 0;
        public int vendorId = 0x0c26;//Vendor ID
        public int productId = 0;//Product ID
        public int portNum = 0;//Port number

        public SerialPort(int deviceId, int vendorId, int productId, int portNum) {
            this.deviceId = deviceId;
            this.vendorId = vendorId;
            this.productId = productId;
            this.portNum = portNum;
        }

        @SuppressLint("DefaultLocale")
        @Override
        public String toString() {
            return String.format("SerialPort:deviceId=0x%04X, vendorId=0x%04X, portNum=%d"
                    , deviceId, vendorId, portNum);
        }

        @SuppressLint("DefaultLocale")
        public String information() {
            return String.format("\\0x%04X\\0x%04X\\0x%04X\\0x%d", deviceId, vendorId, productId, portNum);
        }
    }
}
