package com.bg7yoz.ft8cn.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.wave.UsbAudioDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * 音频设备选择列表适配器，用于输入/输出设备的Spinner。
 * 同时列出AudioManager识别的设备和通过UsbManager发现的USB音频设备。
 */
public class AudioDeviceSpinnerAdapter extends BaseAdapter {
    private final Context mContext;
    private final int deviceType; // AudioManager.GET_DEVICES_INPUTS or GET_DEVICES_OUTPUTS
    private final boolean isInput;

    // AudioManager recognized devices
    private final List<AudioDeviceInfo> audioDeviceList = new ArrayList<>();
    // USB audio devices discovered via UsbManager (not in AudioManager)
    private final List<UsbAudioDevice.UsbAudioDeviceInfo> usbAudioDeviceList = new ArrayList<>();

    /**
     * @param context    上下文
     * @param deviceType AudioManager.GET_DEVICES_INPUTS 或 AudioManager.GET_DEVICES_OUTPUTS
     */
    public AudioDeviceSpinnerAdapter(Context context, int deviceType) {
        mContext = context;
        this.deviceType = deviceType;
        this.isInput = (deviceType == AudioManager.GET_DEVICES_INPUTS);
        refreshDevices();
    }

    /**
     * 重新枚举音频设备（AudioManager + USB）
     */
    public void refreshDevices() {
        audioDeviceList.clear();
        usbAudioDeviceList.clear();

        // 1. Enumerate AudioManager devices
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            AudioDeviceInfo[] devices = audioManager.getDevices(deviceType);
            for (AudioDeviceInfo device : devices) {
                audioDeviceList.add(device);
            }
        }

        // 2. Scan for USB Audio Class devices not recognized by AudioManager
        try {
            List<UsbAudioDevice.UsbAudioDeviceInfo> usbDevices =
                    UsbAudioDevice.findUsbAudioDevices(mContext);
            for (UsbAudioDevice.UsbAudioDeviceInfo usbDev : usbDevices) {
                boolean matchesDirection = isInput ? usbDev.hasInput : usbDev.hasOutput;
                if (!matchesDirection) continue;

                // Check if this device is already listed by AudioManager (by matching type)
                boolean alreadyListed = false;
                for (AudioDeviceInfo adi : audioDeviceList) {
                    if (adi.getType() == AudioDeviceInfo.TYPE_USB_DEVICE
                            || adi.getType() == AudioDeviceInfo.TYPE_USB_ACCESSORY
                            || adi.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                        // AudioManager already has a USB audio device — might be the same one
                        // Compare product name if available
                        String adName = adi.getProductName() != null
                                ? adi.getProductName().toString() : "";
                        String usbName = usbDev.device.getProductName() != null
                                ? usbDev.device.getProductName() : "";
                        if (!adName.isEmpty() && !usbName.isEmpty()
                                && adName.equals(usbName)) {
                            alreadyListed = true;
                            break;
                        }
                    }
                }

                if (!alreadyListed) {
                    usbAudioDeviceList.add(usbDev);
                }
            }
        } catch (Exception e) {
            // Ignore USB enumeration errors
        }
    }

    @Override
    public int getCount() {
        // +1 for "Default" entry
        return 1 + audioDeviceList.size() + usbAudioDeviceList.size();
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) return null; // Default
        int adPos = position - 1;
        if (adPos < audioDeviceList.size()) {
            return audioDeviceList.get(adPos);
        }
        int usbPos = adPos - audioDeviceList.size();
        if (usbPos < usbAudioDeviceList.size()) {
            return usbAudioDeviceList.get(usbPos);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * 获取指定位置的设备ID。
     * 正数 = AudioManager设备ID
     * 0 = 默认
     * -1 = USB音频设备
     */
    public int getDeviceId(int position) {
        if (position == 0) return 0;
        int adPos = position - 1;
        if (adPos < audioDeviceList.size()) {
            return audioDeviceList.get(adPos).getId();
        }
        // USB audio device
        return -1;
    }

    /**
     * Get the USB audio device info at the given spinner position, or null.
     */
    public UsbAudioDevice.UsbAudioDeviceInfo getUsbAudioDeviceInfo(int position) {
        if (position == 0) return null;
        int adPos = position - 1;
        if (adPos < audioDeviceList.size()) return null;
        int usbPos = adPos - audioDeviceList.size();
        if (usbPos < usbAudioDeviceList.size()) {
            return usbAudioDeviceList.get(usbPos);
        }
        return null;
    }

    /**
     * 根据设备ID获取在列表中的位置。
     * deviceId == 0 → 0 (Default)
     * deviceId > 0 → search AudioManager devices
     * deviceId == -1 → search USB audio devices by VID:PID
     */
    public int getPositionByDeviceId(int deviceId) {
        if (deviceId == 0) return 0;

        if (deviceId > 0) {
            for (int i = 0; i < audioDeviceList.size(); i++) {
                if (audioDeviceList.get(i).getId() == deviceId) {
                    return i + 1;
                }
            }
        }

        // For USB audio (deviceId == -1), match by VID:PID
        if (deviceId == -1) {
            int vid = isInput
                    ? com.bg7yoz.ft8cn.GeneralVariables.usbAudioInputVendorId
                    : com.bg7yoz.ft8cn.GeneralVariables.usbAudioOutputVendorId;
            int pid = isInput
                    ? com.bg7yoz.ft8cn.GeneralVariables.usbAudioInputProductId
                    : com.bg7yoz.ft8cn.GeneralVariables.usbAudioOutputProductId;

            for (int i = 0; i < usbAudioDeviceList.size(); i++) {
                UsbAudioDevice.UsbAudioDeviceInfo info = usbAudioDeviceList.get(i);
                if (info.device.getVendorId() == vid && info.device.getProductId() == pid) {
                    return 1 + audioDeviceList.size() + i;
                }
            }
            // VID:PID not matched but we have USB audio devices — select first one
            if (!usbAudioDeviceList.isEmpty()) {
                return 1 + audioDeviceList.size();
            }
        }

        return 0; // fallback to Default
    }

    @SuppressLint({"ViewHolder", "InflateParams"})
    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        LayoutInflater inflater = LayoutInflater.from(mContext);
        view = inflater.inflate(R.layout.bau_rate_spinner_item, null);
        if (view != null) {
            TextView textView = view.findViewById(R.id.bauRateItemTextView);
            textView.setText(getDeviceDisplayName(position));
        }
        return view;
    }

    private String getDeviceDisplayName(int position) {
        if (position == 0) {
            return mContext.getString(R.string.audio_device_default);
        }

        int adPos = position - 1;
        if (adPos < audioDeviceList.size()) {
            AudioDeviceInfo device = audioDeviceList.get(adPos);
            String productName = device.getProductName() != null
                    ? device.getProductName().toString() : "";
            String typeName = getDeviceTypeName(device.getType());
            if (productName.isEmpty()) {
                return typeName;
            }
            return productName + " (" + typeName + ")";
        }

        int usbPos = adPos - audioDeviceList.size();
        if (usbPos < usbAudioDeviceList.size()) {
            return usbAudioDeviceList.get(usbPos).getDisplayName();
        }

        return "Unknown";
    }

    private String getDeviceTypeName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                return "Built-in Mic";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "Built-in Speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE:
                return "Earpiece";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return "Wired Headset";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                return "Wired Headphones";
            case AudioDeviceInfo.TYPE_USB_DEVICE:
                return "USB Audio";
            case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return "USB Accessory";
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return "USB Headset";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return "Bluetooth SCO";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return "Bluetooth A2DP";
            case AudioDeviceInfo.TYPE_HDMI:
                return "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC:
                return "HDMI ARC";
            case AudioDeviceInfo.TYPE_LINE_ANALOG:
                return "Line Analog";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return "Line Digital";
            case AudioDeviceInfo.TYPE_TELEPHONY:
                return "Telephony";
            case AudioDeviceInfo.TYPE_AUX_LINE:
                return "Aux Line";
            case AudioDeviceInfo.TYPE_FM_TUNER:
                return "FM Tuner";
            case AudioDeviceInfo.TYPE_DOCK:
                return "Dock";
            case AudioDeviceInfo.TYPE_IP:
                return "IP";
            case AudioDeviceInfo.TYPE_BUS:
                return "Bus";
            default:
                return "Audio Device";
        }
    }
}
