package com.bg7yoz.ft8cn.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;

import com.bg7yoz.ft8cn.MainViewModel;
import com.bg7yoz.ft8cn.R;

public class ShareLogsProgressDialog extends Dialog {
    private static final String TAG = "ShareLogsProgressDialog";

    private final MainViewModel mainViewModel;
    private TextView shareDataInfoTextView,shareProgressTextView;
    private ProgressBar shareFileDataProgressBar;
    private final boolean isImportMode;
    //private final int progressMax;


    public ShareLogsProgressDialog(@NonNull Context context
            , MainViewModel projectsViewModel, boolean isImportMode) {
        super(context, R.style.ShareProgressDialog);
        this.mainViewModel = projectsViewModel;
        this.isImportMode = isImportMode;
        //this.progressMax =progressMax;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.share_file_progress_dialog);
        setCancelable(false);//Prevent dismissal by tapping outside
        shareDataInfoTextView = findViewById(R.id.shareDataInfoTextView);
        shareProgressTextView = findViewById(R.id.shareProgressTextView);
        if (isImportMode){
            shareProgressTextView.setText(R.string.share_import_log_data);
        }else {
            shareProgressTextView.setText(R.string.preparing_log_data);
        }

        shareFileDataProgressBar = findViewById(R.id.shareFileDataProgressBar);
        Button cancelShareButton = findViewById(R.id.cancelShareButton);


        mainViewModel.mutableShareInfo.observeForever(new Observer<String>() {
            @Override
            public void onChanged(String s) {
                shareDataInfoTextView.setText(s);
            }
        });
        mainViewModel.mutableSharePosition.observeForever(new Observer<Integer>() {
            @Override
            public void onChanged(Integer integer) {
                shareFileDataProgressBar.setProgress(integer);
            }
        });

        mainViewModel.mutableImportShareRunning.observeForever(new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if (!aBoolean) {
                    dismiss();
                }
            }
        });

        mainViewModel.mutableShareRunning.observeForever(new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if (!aBoolean) {
                    dismiss();
                }
            }
        });


        mainViewModel.mutableShareCount.observeForever(new Observer<Integer>() {
            @Override
            public void onChanged(Integer integer) {
                shareFileDataProgressBar.setMax(integer);
            }
        });

        cancelShareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isImportMode) {
                    mainViewModel.mutableImportShareRunning.postValue(false);
                } else {
                    mainViewModel.mutableShareRunning.postValue(false);
                }
            }
        });

    }
}
