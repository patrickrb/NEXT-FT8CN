package com.bg7yoz.ft8cn.wave;
/**
 * 使用Mic录音的操作。
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
    private int bufferSize = 0;//最小缓冲区大小
    private static final int sampleRateInHz = 12000;//采样率
    private static final int channelConfig = AudioFormat.CHANNEL_IN_MONO; //单声道
    //private static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //量化位数
    private static final int audioFormat = AudioFormat.ENCODING_PCM_FLOAT; //量化位数

    private AudioRecord audioRecord = null;//AudioRecord对象
    private UsbAudioDevice usbAudioDevice = null; // USB音频设备
    private boolean useUsbAudio = false;

    private boolean isRunning = false;//是否处于录音的状态。
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

        //计算最小缓冲区
        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
//        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRateInHz
                , channelConfig, audioFormat, bufferSize);//创建AudioRecorder对象

        //设置首选输入设备
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
     * 根据设备ID查找AudioDeviceInfo
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
            audioRecord.startRecording();//开始录音
        }catch (Exception e){
            ToastMessage.show(String.format(GeneralVariables.getStringFromResource(
                    R.string.recorder_cannot_record),e.getMessage()));
            Log.d(TAG, "startRecord: "+e.getMessage() );
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    //判断是否处于录音状态，state!=3，说明没有处于录音的状态
                    if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                        isRunning = false;
                        Log.d(TAG, String.format("录音失败，状态码：%d", audioRecord.getRecordingState()));
                        break;
                    }

                    //读录音的数据
                    int bufferReadResult = audioRecord.read(buffer, 0, bufferSize,AudioRecord.READ_BLOCKING);

                    if (onDataListener!=null){
                        onDataListener.onDataReceived(buffer,bufferReadResult);
                    }
                }
                try {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();//停止录音
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
     * 停止录音。当录音停止后，监听列表中的监听器全部删除。
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
}
