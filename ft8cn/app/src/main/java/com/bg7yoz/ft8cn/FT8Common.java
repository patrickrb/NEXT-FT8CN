package com.bg7yoz.ft8cn;

/**
 * FT8-related constants.
 * @author BGY70Z
 * @date 2023-03-20
 */
public final class FT8Common {
    public static final int FT8_MODE=0;
    public static final int FT4_MODE=1;
    public static final int SAMPLE_RATE=12000;
    public static final int FT8_SLOT_TIME=15;
    public static final int FT8_SLOT_TIME_MILLISECOND=15000;//milliseconds per cycle
    public static final int FT4_SLOT_TIME_MILLISECOND=7500;
    public static final int FT8_5_SYMBOLS_MILLISECOND=800;//time needed for 5 symbols


    public static final float FT4_SLOT_TIME=7.5f;
    public static final int FT8_SLOT_TIME_M=150;//15 seconds
    public static final int FT8_5_SYMBOLS_TIME_M =8;//duration of 5 symbols: 0.8 seconds
    public static final int FT4_SLOT_TIME_M=75;//7.5 seconds
    public static final int FT8_TRANSMIT_DELAY=500;//default transmit delay duration in milliseconds
    public static final long DEEP_DECODE_TIMEOUT=7*1000;//maximum time range for deep decode
    public static final int DECODE_MAX_ITERATIONS=1;//number of iterations
}
