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

import java.util.ArrayList;
import java.util.List;

/**
 * 音频设备选择列表适配器，用于输入/输出设备的Spinner。
 */
public class AudioDeviceSpinnerAdapter extends BaseAdapter {
    private final Context mContext;
    private final int deviceType; // AudioManager.GET_DEVICES_INPUTS or GET_DEVICES_OUTPUTS
    private final List<AudioDeviceInfo> deviceList = new ArrayList<>();

    /**
     * @param context    上下文
     * @param deviceType AudioManager.GET_DEVICES_INPUTS 或 AudioManager.GET_DEVICES_OUTPUTS
     */
    public AudioDeviceSpinnerAdapter(Context context, int deviceType) {
        mContext = context;
        this.deviceType = deviceType;
        refreshDevices();
    }

    /**
     * 重新枚举音频设备
     */
    public void refreshDevices() {
        deviceList.clear();
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            AudioDeviceInfo[] devices = audioManager.getDevices(deviceType);
            for (AudioDeviceInfo device : devices) {
                deviceList.add(device);
            }
        }
    }

    @Override
    public int getCount() {
        return deviceList.size() + 1; // +1 for "Default" entry
    }

    @Override
    public Object getItem(int position) {
        if (position == 0) return null; // Default
        return deviceList.get(position - 1);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * 获取指定位置的设备ID，0表示默认
     */
    public int getDeviceId(int position) {
        if (position == 0) return 0;
        if (position - 1 < deviceList.size()) {
            return deviceList.get(position - 1).getId();
        }
        return 0;
    }

    /**
     * 根据设备ID获取在列表中的位置，未找到返回0（默认）
     */
    public int getPositionByDeviceId(int deviceId) {
        if (deviceId == 0) return 0;
        for (int i = 0; i < deviceList.size(); i++) {
            if (deviceList.get(i).getId() == deviceId) {
                return i + 1;
            }
        }
        return 0; // fallback to Default if device not found
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
        AudioDeviceInfo device = deviceList.get(position - 1);
        String productName = device.getProductName().toString();
        String typeName = getDeviceTypeName(device.getType());
        if (productName.isEmpty()) {
            return typeName;
        }
        return productName + " (" + typeName + ")";
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
