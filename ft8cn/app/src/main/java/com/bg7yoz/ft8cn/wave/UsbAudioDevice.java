package com.bg7yoz.ft8cn.wave;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * USB Audio Class 1.0 device handler for direct USB audio communication.
 * Bypasses Android's audio framework to work with USB audio devices
 * (e.g., DigiRig CM108) that the kernel audio driver doesn't support.
 */
public class UsbAudioDevice {
    private static final String TAG = "UsbAudioDevice";

    public static final int USB_CLASS_AUDIO = 0x01;
    public static final int USB_SUBCLASS_AUDIOCONTROL = 0x01;
    public static final int USB_SUBCLASS_AUDIOSTREAMING = 0x02;

    private static final int SET_CUR = 0x01;
    private static final int SAMPLING_FREQ_CONTROL = 0x01;

    // Number of URBs for isochronous ring buffer
    private static final int NUM_URBS = 16;

    private UsbDevice usbDevice;
    private UsbDeviceConnection connection;

    // Input (microphone)
    private UsbInterface streamingInterfaceIn;
    private UsbEndpoint endpointIn;
    private int inputSampleRate = 48000;
    private int inputChannels = 1;
    private volatile boolean capturing = false;

    // Output (speaker)
    private UsbInterface streamingInterfaceOut;
    private UsbEndpoint endpointOut;
    private int outputSampleRate = 48000;
    private int outputChannels = 2;

    // Singleton active device for use by MicRecorder / FT8TransmitSignal
    private static UsbAudioDevice activeInputDevice;
    private static UsbAudioDevice activeOutputDevice;

    public interface AudioInputCallback {
        void onAudioData(float[] data, int length);
    }

    /**
     * Scan for USB devices with USB Audio Class streaming interfaces.
     */
    public static List<UsbAudioDeviceInfo> findUsbAudioDevices(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return Collections.emptyList();

        List<UsbAudioDeviceInfo> result = new ArrayList<>();
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            boolean hasInput = false;
            boolean hasOutput = false;

            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface iface = device.getInterface(i);
                if (iface.getInterfaceClass() == USB_CLASS_AUDIO
                        && iface.getInterfaceSubclass() == USB_SUBCLASS_AUDIOSTREAMING
                        && iface.getEndpointCount() > 0) {

                    for (int j = 0; j < iface.getEndpointCount(); j++) {
                        UsbEndpoint ep = iface.getEndpoint(j);
                        if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                            if (ep.getDirection() == UsbConstants.USB_DIR_IN) hasInput = true;
                            if (ep.getDirection() == UsbConstants.USB_DIR_OUT) hasOutput = true;
                        }
                    }
                }
            }

            if (hasInput || hasOutput) {
                result.add(new UsbAudioDeviceInfo(device, hasInput, hasOutput));
            }
        }
        return result;
    }

    /**
     * Find a USB audio device by vendor and product ID.
     */
    public static UsbDevice findDeviceByVidPid(Context context, int vendorId, int productId) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return null;
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getVendorId() == vendorId && device.getProductId() == productId) {
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    UsbInterface iface = device.getInterface(i);
                    if (iface.getInterfaceClass() == USB_CLASS_AUDIO) {
                        return device;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Open the USB audio device and discover audio endpoints.
     */
    public boolean open(Context context, UsbDevice device) {
        this.usbDevice = device;
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return false;

        if (!usbManager.hasPermission(device)) {
            Log.e(TAG, "No USB permission for audio device");
            return false;
        }

        this.connection = usbManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open USB audio device");
            return false;
        }

        findEndpoints();
        return endpointIn != null || endpointOut != null;
    }

    private void findEndpoints() {
        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            UsbInterface iface = usbDevice.getInterface(i);
            if (iface.getInterfaceClass() != USB_CLASS_AUDIO
                    || iface.getInterfaceSubclass() != USB_SUBCLASS_AUDIOSTREAMING
                    || iface.getEndpointCount() == 0) {
                continue;
            }

            for (int j = 0; j < iface.getEndpointCount(); j++) {
                UsbEndpoint ep = iface.getEndpoint(j);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN && endpointIn == null) {
                        streamingInterfaceIn = iface;
                        endpointIn = ep;
                        Log.d(TAG, "Found audio input endpoint: addr=0x"
                                + Integer.toHexString(ep.getAddress())
                                + " maxPacket=" + ep.getMaxPacketSize());
                    } else if (ep.getDirection() == UsbConstants.USB_DIR_OUT && endpointOut == null) {
                        streamingInterfaceOut = iface;
                        endpointOut = ep;
                        Log.d(TAG, "Found audio output endpoint: addr=0x"
                                + Integer.toHexString(ep.getAddress())
                                + " maxPacket=" + ep.getMaxPacketSize());
                    }
                }
            }
        }

        // Detect channel count from max packet size
        // USB audio at 48kHz, 16-bit: mono=96 bytes/ms, stereo=192 bytes/ms
        if (endpointIn != null) {
            int mps = endpointIn.getMaxPacketSize();
            // samples_per_ms = sampleRate / 1000 = 48
            // bytes_per_ms = samples_per_ms * channels * 2 (16-bit)
            inputChannels = mps / (48 * 2);
            if (inputChannels < 1) inputChannels = 1;
            if (inputChannels > 2) inputChannels = 2;
            Log.d(TAG, "Input channels detected: " + inputChannels);
        }
        if (endpointOut != null) {
            int mps = endpointOut.getMaxPacketSize();
            outputChannels = mps / (48 * 2);
            if (outputChannels < 1) outputChannels = 1;
            if (outputChannels > 2) outputChannels = 2;
            Log.d(TAG, "Output channels detected: " + outputChannels);
        }
    }

    /**
     * Activate the audio input (microphone) stream.
     */
    public boolean activateInput(int sampleRate) {
        if (streamingInterfaceIn == null || endpointIn == null) return false;

        if (!connection.claimInterface(streamingInterfaceIn, true)) {
            Log.e(TAG, "Failed to claim input interface");
            return false;
        }

        connection.setInterface(streamingInterfaceIn);

        inputSampleRate = sampleRate;
        setSampleRate(endpointIn.getAddress(), sampleRate);
        // If setSampleRate fails, the device may use its default rate (usually 48kHz)
        // which is fine — we resample anyway

        Log.d(TAG, "Input activated at " + inputSampleRate + " Hz");
        return true;
    }

    /**
     * Activate the audio output (speaker) stream.
     */
    public boolean activateOutput(int sampleRate) {
        if (streamingInterfaceOut == null || endpointOut == null) return false;

        if (!connection.claimInterface(streamingInterfaceOut, true)) {
            Log.e(TAG, "Failed to claim output interface");
            return false;
        }

        connection.setInterface(streamingInterfaceOut);

        outputSampleRate = sampleRate;
        setSampleRate(endpointOut.getAddress(), sampleRate);

        Log.d(TAG, "Output activated at " + outputSampleRate + " Hz");
        return true;
    }

    private void setSampleRate(int endpointAddress, int sampleRate) {
        byte[] data = new byte[3];
        data[0] = (byte) (sampleRate & 0xFF);
        data[1] = (byte) ((sampleRate >> 8) & 0xFF);
        data[2] = (byte) ((sampleRate >> 16) & 0xFF);

        int result = connection.controlTransfer(
                0x22, // USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_ENDPOINT
                SET_CUR,
                SAMPLING_FREQ_CONTROL << 8,
                endpointAddress,
                data, data.length, 1000);

        if (result < 0) {
            Log.w(TAG, "setSampleRate failed for rate " + sampleRate
                    + " endpoint 0x" + Integer.toHexString(endpointAddress)
                    + " result=" + result);
        }
    }

    /**
     * Start continuous audio capture. Data is delivered to the callback
     * as mono float at targetSampleRate, matching MicRecorder's format.
     */
    public void startCapture(int targetSampleRate, AudioInputCallback callback) {
        if (endpointIn == null) return;
        capturing = true;

        final int packetSize = endpointIn.getMaxPacketSize();
        final int ratio = inputSampleRate / targetSampleRate;

        new Thread(() -> {
            captureLoop(targetSampleRate, ratio, packetSize, callback);
        }, "USB-Audio-Capture").start();
    }

    @SuppressWarnings("deprecation")
    private void captureLoop(int targetRate, int ratio, int packetSize,
                             AudioInputCallback callback) {
        ByteBuffer[] buffers = new ByteBuffer[NUM_URBS];
        UsbRequest[] requests = new UsbRequest[NUM_URBS];

        try {
            // Initialize and queue URBs
            for (int i = 0; i < NUM_URBS; i++) {
                buffers[i] = ByteBuffer.allocateDirect(packetSize);
                buffers[i].order(ByteOrder.LITTLE_ENDIAN);
                requests[i] = new UsbRequest();
                requests[i].initialize(connection, endpointIn);
                requests[i].setClientData(i);
                buffers[i].clear();
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    requests[i].queue(buffers[i]);
                } else {
                    requests[i].queue(buffers[i], packetSize);
                }
            }

            // Accumulate mono float samples before decimation
            ArrayList<Float> accumulator = new ArrayList<>(inputSampleRate);
            float[] outputBuffer = new float[targetRate]; // 1s max

            while (capturing) {
                UsbRequest completed = connection.requestWait();
                if (completed == null) {
                    if (capturing) Log.e(TAG, "requestWait returned null");
                    break;
                }

                int bufIndex = (int) completed.getClientData();
                ByteBuffer buf = buffers[bufIndex];

                // Process received audio data (16-bit PCM)
                buf.flip();
                int bytesReceived = buf.remaining();
                int totalSamples = bytesReceived / 2; // 16-bit = 2 bytes

                for (int i = 0; i < totalSamples; i++) {
                    if (buf.remaining() < 2) break;
                    short sample = buf.getShort();
                    // If stereo, skip right channel to get mono
                    if (inputChannels == 2 && (i % 2) == 1) continue;
                    accumulator.add(sample / 32768.0f);
                }

                // Decimate and deliver when we have enough
                if (ratio > 0 && accumulator.size() >= ratio) {
                    int outputSamples = accumulator.size() / ratio;
                    if (outputSamples > outputBuffer.length) {
                        outputBuffer = new float[outputSamples * 2];
                    }

                    for (int i = 0; i < outputSamples; i++) {
                        // Average over decimation window for anti-aliasing
                        float sum = 0;
                        int base = i * ratio;
                        for (int j = 0; j < ratio && (base + j) < accumulator.size(); j++) {
                            sum += accumulator.get(base + j);
                        }
                        outputBuffer[i] = sum / ratio;
                    }

                    // Keep leftover samples
                    int consumed = outputSamples * ratio;
                    if (consumed < accumulator.size()) {
                        ArrayList<Float> leftover = new ArrayList<>(
                                accumulator.subList(consumed, accumulator.size()));
                        accumulator.clear();
                        accumulator.addAll(leftover);
                    } else {
                        accumulator.clear();
                    }

                    callback.onAudioData(outputBuffer, outputSamples);
                }

                // Re-queue the URB
                buf.clear();
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    completed.queue(buffers[bufIndex]);
                } else {
                    completed.queue(buffers[bufIndex], packetSize);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Capture loop error: " + e.getMessage());
        } finally {
            for (int i = 0; i < NUM_URBS; i++) {
                if (requests[i] != null) {
                    try { requests[i].cancel(); } catch (Exception ignored) {}
                    try { requests[i].close(); } catch (Exception ignored) {}
                }
            }
        }
    }

    public void stopCapture() {
        capturing = false;
    }

    /**
     * Write audio data to the USB output device.
     * Handles sample rate conversion and format conversion.
     *
     * @param audioData        Float PCM mono data
     * @param sourceSampleRate Sample rate of the input data
     * @return true if successful
     */
    @SuppressWarnings("deprecation")
    public boolean writeAudio(float[] audioData, int sourceSampleRate) {
        if (endpointOut == null || connection == null) return false;

        // Resample to device's output rate
        float[] resampled;
        if (sourceSampleRate != outputSampleRate) {
            resampled = resample(audioData, sourceSampleRate, outputSampleRate);
        } else {
            resampled = audioData;
        }

        // Convert mono float to 16-bit PCM (stereo if device is stereo)
        int samplesPerChannel = resampled.length;
        byte[] pcmData = new byte[samplesPerChannel * outputChannels * 2];
        ByteBuffer bb = ByteBuffer.wrap(pcmData);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < samplesPerChannel; i++) {
            short s = (short) Math.max(-32768, Math.min(32767, resampled[i] * 32768.0f));
            bb.putShort(s); // left (or mono)
            if (outputChannels == 2) {
                bb.putShort(s); // right = same as left
            }
        }

        // Write in chunks matching max packet size
        int packetSize = endpointOut.getMaxPacketSize();
        int offset = 0;

        while (offset < pcmData.length) {
            int chunkSize = Math.min(packetSize, pcmData.length - offset);
            ByteBuffer buf = ByteBuffer.allocateDirect(chunkSize);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            buf.put(pcmData, offset, chunkSize);
            buf.flip();

            UsbRequest request = new UsbRequest();
            request.initialize(connection, endpointOut);
            boolean queued;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                queued = request.queue(buf);
            } else {
                queued = request.queue(buf, chunkSize);
            }
            if (!queued) {
                Log.e(TAG, "Failed to queue output URB at offset " + offset);
                request.close();
                return false;
            }

            UsbRequest completed = connection.requestWait();
            if (completed != null) {
                completed.close();
            }

            offset += chunkSize;
        }

        return true;
    }

    /**
     * Linear interpolation resampler.
     */
    private float[] resample(float[] input, int fromRate, int toRate) {
        if (fromRate == toRate) return input;

        double ratio = (double) toRate / fromRate;
        int outputLen = (int) (input.length * ratio);
        float[] output = new float[outputLen];

        for (int i = 0; i < outputLen; i++) {
            double srcIndex = i / ratio;
            int idx = (int) srcIndex;
            double frac = srcIndex - idx;

            if (idx + 1 < input.length) {
                output[i] = (float) (input[idx] * (1 - frac) + input[idx + 1] * frac);
            } else if (idx < input.length) {
                output[i] = input[idx];
            }
        }

        return output;
    }

    public void close() {
        stopCapture();
        if (connection != null) {
            try {
                if (streamingInterfaceIn != null) {
                    connection.releaseInterface(streamingInterfaceIn);
                }
            } catch (Exception ignored) {}
            try {
                if (streamingInterfaceOut != null) {
                    connection.releaseInterface(streamingInterfaceOut);
                }
            } catch (Exception ignored) {}
            connection.close();
            connection = null;
        }
    }

    public boolean hasInput() { return endpointIn != null; }
    public boolean hasOutput() { return endpointOut != null; }
    public UsbDevice getUsbDevice() { return usbDevice; }
    public int getInputSampleRate() { return inputSampleRate; }
    public int getOutputSampleRate() { return outputSampleRate; }

    // --- Singleton management for active devices ---

    public static void setActiveInputDevice(UsbAudioDevice device) {
        if (activeInputDevice != null && activeInputDevice != device) {
            activeInputDevice.stopCapture();
        }
        activeInputDevice = device;
    }

    public static UsbAudioDevice getActiveInputDevice() {
        return activeInputDevice;
    }

    public static void setActiveOutputDevice(UsbAudioDevice device) {
        activeOutputDevice = device;
    }

    public static UsbAudioDevice getActiveOutputDevice() {
        return activeOutputDevice;
    }

    public static void closeActiveDevices() {
        if (activeInputDevice != null) {
            activeInputDevice.close();
            activeInputDevice = null;
        }
        if (activeOutputDevice != null) {
            activeOutputDevice.close();
            activeOutputDevice = null;
        }
    }

    /**
     * Describes a discovered USB audio device.
     */
    public static class UsbAudioDeviceInfo {
        public final UsbDevice device;
        public final boolean hasInput;
        public final boolean hasOutput;

        public UsbAudioDeviceInfo(UsbDevice device, boolean hasInput, boolean hasOutput) {
            this.device = device;
            this.hasInput = hasInput;
            this.hasOutput = hasOutput;
        }

        public String getDisplayName() {
            String name = device.getProductName();
            if (name == null || name.isEmpty()) {
                name = String.format("USB Audio [%04X:%04X]",
                        device.getVendorId(), device.getProductId());
            }
            return name + " (USB)";
        }
    }
}
