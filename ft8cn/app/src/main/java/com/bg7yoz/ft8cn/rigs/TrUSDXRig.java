
/**
 * (tr)uSDX, fork from KENWOOD TS590.
 * Based on v0.9, adding TrSDXRig support.
 *
 * @author Sunguk Lee
 * 2023-08-16
 */
package com.bg7yoz.ft8cn.rigs;

import static com.bg7yoz.ft8cn.GeneralVariables.QUERY_FREQ_TIMEOUT;
import static com.bg7yoz.ft8cn.GeneralVariables.START_QUERY_FREQ_DELAY;

import android.os.Handler;
import android.util.Log;

import com.bg7yoz.ft8cn.Ft8Message;
import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.R;
import com.bg7yoz.ft8cn.database.ControlMode;
import com.bg7yoz.ft8cn.ft8transmit.GenerateFT8;
import com.bg7yoz.ft8cn.ui.ToastMessage;
import com.bg7yoz.ft8cn.wave.FT8Resample;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

/**
 * (tr)uSDX, fork from KENWOOD TS590.
 * 2023-08-16 Modification submitted by DS1UFX (based on v0.9), adding (tr)uSDX audio over CAT support.
 */
public class TrUSDXRig extends BaseRig {
    private static final String TAG = "TrUSDXRig";
    private static final int rxSampling = 7812;
    private static final int txSampling = 11520;
    private final StringBuilder buffer = new StringBuilder();
    private final ByteArrayOutputStream rxStreamBuffer = new ByteArrayOutputStream();

    private Timer readFreqTimer = new Timer();
    private int swr = 0;
    private int alc = 0;
    private boolean alcMaxAlert = false;
    private boolean swrAlert = false;
    private boolean rxStreaming = false;

    private TimerTask readTask() {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    if (!isConnected()) {
                        readFreqTimer.cancel();
                        readFreqTimer.purge();
                        readFreqTimer = null;
                        return;
                    }
                    if (isPttOn()) {
                        clearBufferData();
                    } else {
                        readFreqFromRig();//read frequency
                    }

                } catch (Exception e) {
                    Log.e(TAG, "readFreq error:" + e.getMessage());
                }
            }
        };
    }

    /**
     * Clear buffer data
     */
    private void clearBufferData() {
        buffer.setLength(0);
    }

    @Override
    public void setPTT(boolean on) {
        super.setPTT(on);
        if (getConnector() != null) {
            switch (getControlMode()) {
                case ControlMode.CAT:
                    if (on) {
                        rxStreaming = false;
                    }
                    getConnector().setPttOn(KenwoodTK90RigConstant.setTrUSDXPTTState(on));
                    break;
                case ControlMode.RTS:
                case ControlMode.DTR:
                    getConnector().setPttOn(on);
                    break;
            }
        }
    }

    @Override
    public boolean isConnected() {
        if (getConnector() == null) {
            return false;
        }
        return getConnector().isConnected();
    }

    @Override
    public void setUsbModeToRig() {
        if (getConnector() != null) {
            getConnector().sendData(KenwoodTK90RigConstant.setTS590OperationUSBMode());
        }
    }

    @Override
    public void setFreqToRig() {
        if (getConnector() != null) {
            getConnector().sendData(KenwoodTK90RigConstant.setTS590OperationFreq(getFreq()));
        }
    }

    @Override
    public void onReceiveData(byte[] data) {
        byte[] remain = data;
        String s = new String(data);
        while (s.contains(";")) { // ;
            // TODO apply effective way
            int idx = s.indexOf(";");
            byte[] cutted = Arrays.copyOf(remain, idx);
            remain = Arrays.copyOfRange(remain, idx + 1, remain.length);
            s = new String(remain);

            if (rxStreaming) {
                onReceivedWaveData(cutted, true);
                rxStreaming = false;
            } else {
                buffer.append(new String(cutted));
                //begin parsing data
                Yaesu3Command yaesu3Command = Yaesu3Command.getCommand(buffer.toString());
                clearBufferData();//clear buffer

                if (yaesu3Command == null) {
                    continue;
                }
                String cmd = yaesu3Command.getCommandID();
                if (cmd.equalsIgnoreCase("FA")) {//frequency
                    long tempFreq = Yaesu3Command.getFrequency(yaesu3Command);
                    if (tempFreq != 0) {//if tempFreq==0, frequency is invalid
                        setFreq(Yaesu3Command.getFrequency(yaesu3Command));
                    }
                } else if (cmd.equalsIgnoreCase("US")) {
                    rxStreaming = true;
                    byte[] wave = Arrays.copyOfRange(cutted, 2, cutted.length);
                    onReceivedWaveData(wave);
                }
            }
        }
        if (remain.length <= 0) {
            return;
        }
        if (rxStreaming) {
            onReceivedWaveData(remain);
        } else if (remain.length >= 2 && remain[0] == 0x55 && remain[1] == 0x53) {// US
            clearBufferData();
            rxStreaming = true;
            byte[] wave = Arrays.copyOfRange(remain, 2, remain.length);
            onReceivedWaveData(wave);
        } else {
            buffer.append(s);
        }
    }

    private void showAlert() {
        if ((swr >= KenwoodTK90RigConstant.ts_590_swr_alert_max)
                && GeneralVariables.swr_switch_on) {
            if (!swrAlert) {
                swrAlert = true;
                ToastMessage.show(GeneralVariables.getStringFromResource(R.string.swr_high_alert));
            }
        } else {
            swrAlert = false;
        }
        if ((alc > KenwoodTK90RigConstant.ts_590_alc_alert_max)
                && GeneralVariables.alc_switch_on) {//ALC alert
            if (!alcMaxAlert) {
                alcMaxAlert = true;
                ToastMessage.show(GeneralVariables.getStringFromResource(R.string.alc_high_alert));
            }
        } else {
            alcMaxAlert = false;
        }

    }

    @Override
    public void readFreqFromRig() {
        if (getConnector() != null) {
            clearBufferData();//clear buffer
            // force reset
            getConnector().sendData(KenwoodTK90RigConstant.setTrUSDXPTTState(false));
            getConnector().sendData(KenwoodTK90RigConstant.setTS590ReadOperationFreq());
        }
    }

    @Override
    public String getName() {
        return "(tr)uSDX";
    }

    @Override
    public boolean supportWaveOverCAT() {
        return true;
    }

    @Override
    public void onDisconnecting() {
        if (getConnector() != null) {
            clearBufferData();
            getConnector().sendData(KenwoodTK90RigConstant.setTrUSDXStreaming(false));
        }
    }


    /**
     * After receiving audio data, convert the sample rate from 7812Hz to 12000Hz and send to Connector.
     *
     * @param data received audio data (7812Hz)
     */
    public void onReceivedWaveData(byte[] data) {
        onReceivedWaveData(data, false);
    }


    /**
     * After receiving audio data, convert the sample rate from 7812Hz to 12000Hz and send to Connector.
     *
     * @param data  received audio data (7812Hz)
     * @param force whether to force conversion
     */
    public void onReceivedWaveData(byte[] data, boolean force) {
        if (data.length == 0) {
            return;
        }
        if (getConnector() == null) {
            return;
        }
        //Resample rxResample = new Resample(Resample.ConverterType.SRC_LINEAR, 1
        //        , rxSampling, 12000);

        rxStreamBuffer.write(data, 0, data.length);
        if (rxStreamBuffer.size() >= 256 || force) {//8-bit to 16-bit, 7812Hz to 12000Hz
            //byte[] resampled = rxResample.processCopy(toWaveSamples8To16(rxStreamBuffer.toByteArray()));
            float[] resampled = FT8Resample.get32Resample16(
                    toWaveSamples8To16Int(rxStreamBuffer.toByteArray()), rxSampling, 12000, 1);
            rxStreamBuffer.reset();
            getConnector().receiveWaveData(resampled);
        }
        //rxResample.close();
    }

    @Override
    public void sendWaveData(Ft8Message message) {
        if (getConnector() == null) {
            return;
        }
        float[] wave = GenerateFT8.generateFt8(message, GeneralVariables.getBaseFrequency()
                , 24000);

        if (wave == null) {
            setPTT(false);
            return;
        }
        //adjust signal strength
        for (int i = 0; i < wave.length; i++) {
            wave[i] = wave[i] * GeneralVariables.volumePercent;
        }

//
//        byte[] pcm16 = toWaveFloatToPCM16(wave);
//        Resample txResample = new Resample(Resample.ConverterType.SRC_SINC_FASTEST, 1
//                , 24000, txSampling);
//        byte[] resampled = txResample.processCopy(pcm16);
//        txResample.close();
//        byte[] pcm8 = toWaveSamples16To8(resampled);

        byte[] pcm8 = FT8Resample.get8Resample32(wave, 24000, txSampling, 1);


        for (int i = 0; i < pcm8.length; i++) {
            if (pcm8[i] == 0x3B) pcm8[i] = 0x3A; // ; to :
        }
        while (pcm8.length > 0) {
            if (pcm8.length <= 256) {
                getConnector().sendData(pcm8);
                break;
            } else {
                getConnector().sendData(Arrays.copyOfRange(pcm8, 0, 256));
                pcm8 = Arrays.copyOfRange(pcm8, 256, pcm8.length);
            }
        }
    }

    /**
     * Convert 8-bit audio samples to 16-bit sample depth
     *
     * @param in 8-bit data
     * @return 16-bit data (byte type)
     */
    private static byte[] toWaveSamples8To16(byte[] in) {
        ByteBuffer buf = ByteBuffer.allocate(in.length * 2);
        for (int i = 0; i < in.length; i++) {
            short v = (short) (((short) in[i] - 128) << 8);
            buf.putShort(v);
        }
        return buf.array();
    }

    /**
     * Convert 8-bit audio samples to 16-bit sample depth
     *
     * @param in 8-bit data
     * @return 16-bit data (short type)
     */
    private static short[] toWaveSamples8To16Int(byte[] in) {
        short[] buf = new short[in.length];
        for (int i = 0; i < in.length; i++) {
            buf[i] = (short) (((short) in[i] - 128) << 8);
        }
        return buf;
    }

    private static byte[] toWaveFloatToPCM16(float[] in) {
        ByteBuffer buf = ByteBuffer.allocate(in.length * 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < in.length; i++) {
            float x = in[i];
            short v = (short) (x * 32767.0f);
            buf.putShort(v);
        }
        return buf.array();
    }

    private static byte[] toWaveFloatToPCM8(float[] in) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            float x = in[i];
            short v = (short) (x * 32767.0f);
            out[i] = (byte) ((byte) (v >> 8) + 128);
        }
        return out;
    }

    /**
     * Convert 16-bit data to 8-bit
     *
     * @param in 16-bit data (bytes)
     * @return 8-bit bytes
     */
    private static byte[] toWaveSamples16To8(byte[] in) {
        byte[] out = new byte[in.length / 2];
        for (int i = 0; i < out.length; i++) {
            short v = readShortBigEndianData(in, i * 2);
            out[i] = (byte) (((byte) (v >> 8)) + 128);
        }
        return out;
    }

    private static byte[] toWaveSamples16To8(short[] in) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (((byte) (in[i] >> 8)) + 128);
        }
        return out;
    }

    public TrUSDXRig() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (getConnector() != null) {
                    getConnector().sendData(KenwoodTK90RigConstant.setTS590VFOMode());
                    //changed to set USB mode
                    getConnector().sendData(KenwoodTK90RigConstant.setTS590OperationUSBMode());
                    getConnector().sendData(KenwoodTK90RigConstant.setTrUSDXStreaming(true));
                }
            }
        }, START_QUERY_FREQ_DELAY - 500);
        readFreqTimer.schedule(readTask(), START_QUERY_FREQ_DELAY, QUERY_FREQ_TIMEOUT);
    }

    /**
     * Read little-endian Short from stream data
     *
     * @param data  stream data
     * @param start start position
     * @return Int16
     */
    public static short readShortBigEndianData(byte[] data, int start) {
        if (data.length - start < 2) return 0;
        return (short) ((short) data[start] & 0xff
                | ((short) data[start + 1] & 0xff) << 8);
    }

}