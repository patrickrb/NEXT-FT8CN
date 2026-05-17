package com.bg7yoz.ft8cn.wave;
/**
 * Operations for recording using the microphone.
 * @author BGY70Z
 * @date 2023-03-20
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.ui.ToastMessage;

public class MicRecorder {
    private static final String TAG = "MicRecorder";
    private int bufferSize = 0;//minimum buffer size
    private static final int sampleRateInHz = 12000;//sampling rate
    private static final int channelConfig = AudioFormat.CHANNEL_IN_MONO; //mono
    //private static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //quantization bit depth
    private static final int audioFormat = AudioFormat.ENCODING_PCM_FLOAT; //quantization bit depth

    private AudioRecord audioRecord = null;//AudioRecord object
    private UsbAudioDevice usbAudioDevice = null; // USB audio device
    private boolean useUsbAudio = false;

    private volatile boolean isRunning = false;//whether currently in recording state
    private OnDataListener onDataListener;

    public interface OnDataListener{
        void onDataReceived(float[] data,int len);
    }

    @SuppressLint("MissingPermission")
    public MicRecorder(){
        // Check if USB audio input is selected
        if (GeneralVariables.audioInputDeviceId == -1
                && GeneralVariables.usbAudioInputVendorId != 0) {
            usbAudioDevice = openUsbAudioInput();
            if (usbAudioDevice != null) {
                useUsbAudio = true;
                UsbAudioDevice.setActiveInputDevice(usbAudioDevice);
                Log.d(TAG, "Using USB audio input device");
                return; // Skip AudioRecord setup
            }
            Log.w(TAG, "USB audio device not available, falling back to default");
        }

        //calculate minimum buffer size
        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
//        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRateInHz
                , channelConfig, audioFormat, bufferSize);//create AudioRecorder object

        //set preferred input device
        if (GeneralVariables.audioInputDeviceId > 0) {
            AudioDeviceInfo deviceInfo = findAudioDeviceById(
                    GeneralVariables.audioInputDeviceId, AudioManager.GET_DEVICES_INPUTS);
            audioRecord.setPreferredDevice(deviceInfo); // null resets to default
        }
    }

    /**
     * Open and configure a USB audio device for input.
     */
    private UsbAudioDevice openUsbAudioInput() {
        Context context = GeneralVariables.getMainContext();
        if (context == null) return null;

        UsbDevice device = UsbAudioDevice.findDeviceByVidPid(context,
                GeneralVariables.usbAudioInputVendorId,
                GeneralVariables.usbAudioInputProductId);
        if (device == null) {
            Log.w(TAG, String.format("USB audio device not found: %04X:%04X",
                    GeneralVariables.usbAudioInputVendorId,
                    GeneralVariables.usbAudioInputProductId));
            return null;
        }

        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null || !usbManager.hasPermission(device)) {
            Log.w(TAG, "No USB permission for audio device");
            return null;
        }

        UsbAudioDevice usbDev = new UsbAudioDevice();
        if (!usbDev.open(context, device)) {
            Log.e(TAG, "Failed to open USB audio device");
            return null;
        }

        if (!usbDev.hasInput()) {
            Log.e(TAG, "USB audio device has no input endpoint");
            usbDev.close();
            return null;
        }

        if (!usbDev.activateInput(48000)) {
            Log.e(TAG, "Failed to activate USB audio input");
            usbDev.close();
            return null;
        }

        return usbDev;
    }

    /**
     * Find AudioDeviceInfo by device ID
     */
    private AudioDeviceInfo findAudioDeviceById(int deviceId, int deviceType) {
        Context context = GeneralVariables.getMainContext();
        if (context == null) return null;
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return null;
        AudioDeviceInfo[] devices = audioManager.getDevices(deviceType);
        for (AudioDeviceInfo device : devices) {
            if (device.getId() == deviceId) {
                return device;
            }
        }
        return null;
    }

    public void start(){
        if (isRunning) return;
        isRunning = true;

        if (useUsbAudio && usbAudioDevice != null) {
            startUsbCapture();
        } else {
            startAudioRecordCapture();
        }
    }

    private void startUsbCapture() {
        usbAudioDevice.startCapture(sampleRateInHz, new UsbAudioDevice.AudioInputCallback() {
            @Override
            public void onAudioData(float[] data, int length) {
                if (isRunning && onDataListener != null) {
                    onDataListener.onDataReceived(data, length);
                }
            }
        });
    }

    private void startAudioRecordCapture() {
        float[] buffer = new float[bufferSize];
        try {
            audioRecord.startRecording();//start recording
        }catch (Exception e){
            ToastMessage.show(String.format(GeneralVariables.getStringFromResource(
                    R.string.recorder_cannot_record),e.getMessage()));
            Log.d(TAG, "startRecord: "+e.getMessage() );
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    //check if in recording state; state!=3 means not in recording state
                    if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        isRunning = false;
                        Log.d(TAG, String.format("Recording failed, state code: %d", audioRecord.getRecordingState()));
                        break;
                    }

                    //read recording data
                    int bufferReadResult = audioRecord.read(buffer, 0, bufferSize,AudioRecord.READ_BLOCKING);

                    if (onDataListener!=null){
                        onDataListener.onDataReceived(buffer,bufferReadResult);
                    }
                }
                try {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();//stop recording
                    }
                }catch (Exception e){
                    ToastMessage.show(String.format(GeneralVariables.getStringFromResource(
                            R.string.recorder_stop_record_error),e.getMessage()));
                    Log.d(TAG, "startRecord: "+e.getMessage() );
                }
            }
        }).start();
    }

    /**
     * Stop recording. When recording stops, all monitors in the listener list are removed.
     */
    public void stopRecord() {
        isRunning = false;
        if (useUsbAudio && usbAudioDevice != null) {
            usbAudioDevice.stopCapture();
        }
    }

    public OnDataListener getOnDataListener() {
        return onDataListener;
    }

    public void setOnDataListener(OnDataListener onDataListener) {
        this.onDataListener = onDataListener;
    }

    /**
     * Reinitialize the audio input device. Stops current audio capture,
     * re-checks for USB audio device availability, and restarts if it was running.
     * This handles the case where a USB audio device is connected after MicRecorder
     * was originally constructed.
     */
    @SuppressLint("MissingPermission")
    public void reinitialize() {
        boolean wasRunning = isRunning;
        OnDataListener savedListener = onDataListener;

        // Stop current capture
        stopRecord();

        // Release old AudioRecord (stopRecord only sets isRunning=false)
        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (Exception e) {
                Log.d(TAG, "reinitialize: error releasing AudioRecord: " + e.getMessage());
            }
            audioRecord = null;
        }

        // Close old USB audio device
        if (usbAudioDevice != null) {
            try {
                usbAudioDevice.close();
            } catch (Exception e) {
                Log.d(TAG, "reinitialize: error closing USB audio device: " + e.getMessage());
            }
        }

        // Reset state
        useUsbAudio = false;
        usbAudioDevice = null;

        // Re-check for USB audio input
        if (GeneralVariables.audioInputDeviceId == -1
                && GeneralVariables.usbAudioInputVendorId != 0) {
            usbAudioDevice = openUsbAudioInput();
            if (usbAudioDevice != null) {
                useUsbAudio = true;
                UsbAudioDevice.setActiveInputDevice(usbAudioDevice);
                Log.d(TAG, "reinitialize: Using USB audio input device");
            } else {
                Log.w(TAG, "reinitialize: USB audio device not available, falling back to default");
            }
        }

        // Set up standard AudioRecord if not using USB audio
        if (!useUsbAudio) {
            bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRateInHz,
                    channelConfig, audioFormat, bufferSize);

            if (GeneralVariables.audioInputDeviceId > 0) {
                AudioDeviceInfo deviceInfo = findAudioDeviceById(
                        GeneralVariables.audioInputDeviceId, AudioManager.GET_DEVICES_INPUTS);
                audioRecord.setPreferredDevice(deviceInfo);
            }
        }

        // Restore listener and restart if it was running
        onDataListener = savedListener;
        if (wasRunning) {
            start();
        }
    }
}
