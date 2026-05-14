package com.bg7yoz.ft8cn.database;

/**
 * Callback for querying callsigns
 * @author BGY70Z
 * @date 2023-03-20
 */
public interface OnGetCallsign {
    void  doOnAfterGetCallSign(boolean exists);
}
