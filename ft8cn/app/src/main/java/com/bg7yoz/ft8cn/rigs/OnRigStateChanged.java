package com.bg7yoz.ft8cn.rigs;

/**
 * Callback for rig state changes.
 * @author BGY70Z
 * @date 2023-03-20
 */
public interface OnRigStateChanged {
    void onDisconnected();
    void onConnected();
    void onPttChanged(boolean isOn);
    void onFreqChanged(long freq);
    void onRunError(String message);
}
