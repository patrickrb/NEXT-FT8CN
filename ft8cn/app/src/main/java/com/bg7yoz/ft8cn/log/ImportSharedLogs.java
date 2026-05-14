package com.bg7yoz.ft8cn.log;

import com.bg7yoz.ft8cn.GeneralVariables;
import com.bg7yoz.ft8cn.MainViewModel;
import com.bg7yoz.ft8cn.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class ImportSharedLogs {
    private static final String TAG = "ImportSharedLogs";
    //private final Uri uri;
    private String fileContext;
    private final MainViewModel mainViewModel;
    private InputStream logFileStream = null;
    //private final HashMap<Integer,String> errorLines=new HashMap<>();

    public ImportSharedLogs(MainViewModel mainViewModel) throws IOException {
        this.mainViewModel = mainViewModel;
    }

    private boolean loadData(OnShareLogEvents onShareLogEvents) {
        if (logFileStream != null) {
            byte[] bytes = new byte[0];
            try {
                bytes = new byte[logFileStream.available()];
                logFileStream.read(bytes);
                fileContext = new String(bytes);
            } catch (IOException e) {
                if (onShareLogEvents != null) {
                    onShareLogEvents.onShareFailed(String.format(
                            GeneralVariables.getStringFromResource(R.string.import_share_failed)
                            , e.getMessage()));
                }
                return false;
            }
            return true;

        } else {
            fileContext = "";
            return false;
        }
    }

    public String getLogBody() {
        String[] temp = fileContext.split("[<][Ee][Oo][Hh][>]");
        if (temp.length > 1) {
            return temp[temp.length - 1];
        } else {
            return "";
        }
    }

    /**
     * Gets all records from the log file. Each record is stored as a HashMap where the Key is the field name (uppercase) and the Value is the value.
     *
     * @return Record list. ArrayList
     */
    public ArrayList<HashMap<String, String>> getLogRecords() {
        String[] temp = getLogBody().split("[<][Ee][Oo][Rr][>]");//Extract the raw content of each record
        ArrayList<HashMap<String, String>> records = new ArrayList<>();
        int count = 0;//Parsing counter
        for (String s : temp) {//Parse each raw record content
            count++;
            if (!s.contains("<")) {
                continue;
            }//No tags found, skip parsing
            try {
                HashMap<String, String> record = new HashMap<>();//Create a record
                String[] fields = s.split("<");//Split each field of the record

                for (String field : fields) {//Parse each raw record

                    if (field.length() > 1) {//If it can be parsed
                        String[] values = field.split(">");//Split field name and value

                        if (values.length > 1) {//If it can be parsed
                            if (values[0].contains(":")) {//Split field name and field length; field name before colon, length after
                                String[] ttt = values[0].split(":");
                                if (ttt.length > 1) {
                                    String name = ttt[0];//Field name
                                    int valueLen = Integer.parseInt(ttt[1]);//Field length
                                    if (valueLen > 0) {
                                        if (values[1].length() < valueLen) {
                                            valueLen = values[1].length() - 1;
                                        }
                                        String value = values[1].substring(0, valueLen);//Field value
                                        record.put(name.toUpperCase(), value);//Save field, key must be uppercase
                                    }
                                }

                            }
                        }
                    }
                }
                records.add(record);//Save record
            } catch (Exception e) {
                //errorLines.put(count,s);//Save the erroneous content.
                //importTask.readErrorCount=errorLines.size();
            }
        }
        return records;
    }

    public void doImport(InputStream logFileStream, OnShareLogEvents onShareLogEvents) {
        this.logFileStream = logFileStream;

        new Thread(new Runnable() {
            @Override
            public void run() {
                //Load data
                if (onShareLogEvents != null) {
                    onShareLogEvents.onPreparing(GeneralVariables.getStringFromResource(R.string.preparing_import_logs));
                }
                if (!loadData(onShareLogEvents)) {
                    return;
                }

                int position = 0;
                ArrayList<HashMap<String, String>> recordList = getLogRecords();//Split by regex: [<][Ee][Oo][Rr][>]
                int count = recordList.size();

                if (onShareLogEvents != null) {
                    onShareLogEvents.onShareStart(count, String.format(
                            GeneralVariables.getStringFromResource(R.string.total_logs)
                            , count));
                }


                for (HashMap<String, String> record : recordList) {
                    position++;
                    QSLRecord qslRecord = new QSLRecord(record);

                    mainViewModel.databaseOpr.doInsertQSLData(qslRecord, null);

                    if (onShareLogEvents != null) {
                        if (!onShareLogEvents.onShareProgress(count, position
                                , String.format(GeneralVariables.getStringFromResource(R.string.share_logs_been_read)
                                        , position))) {
                            break;
                        }
                    }


                }

                if (onShareLogEvents != null) {
                    onShareLogEvents.afterGet(count, String.format(
                            GeneralVariables.getStringFromResource(R.string.total_logs)
                            , position));
                }

            }
        }).start();
    }


    public String getFileContext() {
        return fileContext;
    }
}
