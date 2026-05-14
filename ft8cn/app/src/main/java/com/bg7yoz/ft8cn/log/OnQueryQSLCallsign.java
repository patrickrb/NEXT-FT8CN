package com.bg7yoz.ft8cn.log;
/**
 * Callback for querying callsign logs.
 * @author BGY70Z
 * @date 2023-03-20
 */

import java.util.ArrayList;

public interface OnQueryQSLCallsign {
     void afterQuery(ArrayList<QSLCallsignRecord> records);
}
