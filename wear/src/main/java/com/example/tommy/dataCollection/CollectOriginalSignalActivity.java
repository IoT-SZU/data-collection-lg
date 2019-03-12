package com.example.tommy.dataCollection;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import com.example.tommy.dataCollection.services.AudioService;
import com.example.tommy.dataCollection.services.DataCollectService;
import com.example.tommy.dataCollection.utils.FileTransfer;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.wearable.DataApi;

import java.io.File;
import java.util.ArrayList;

public class CollectOriginalSignalActivity extends Activity
    implements DataCollectService.OnReceiveListener {
    Button startBtn;

    ArrayList<Short>[] data;

    DataCollectService service;
    AudioService audioService;
    FileTransfer fileTransfer;

    File audioFile;
    boolean start = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_original_signal);

        startBtn = (Button) findViewById(R.id.startBtn);

        service = DataCollectService.getInstance();
        audioService = new AudioService();

        fileTransfer = new FileTransfer(this);

        data = new ArrayList[6];
        for (int i = 0; i < 6; ++i) {
            data[i] = new ArrayList<>();
        }

        // keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onReceive(short[] res) {
        if (start) {
            if (data[0].size() == 0) {
                showText("开始采集");
            }
            for (int i = 0; i < 6; ++i) {
                data[i].add(res[i]);
            }
        }
    }

    public void clickStart(View view) {
        start = !start;
        startBtn.setText(start ? "Stop" : "Start");

        if (start) {
            service.addEventListener(this);
            audioService.startRecord();
            for (int i = 0; i < 6; ++i) {
                data[i].clear();
            }
        } else {
            service.removeEventListener(this);
            audioFile = audioService.stopRecrod();
        }
    }

    public void clickSave(View view) {
        showText("未实现");
    }

    public void clickSend(final View view) {
        if (data[0].size() == 0) {
            showText("发送失败! 当前数据为空.");
            return;
        }

        showText("开始发送...");
        view.setEnabled(false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean res = fileTransfer.send(data, audioFile, new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        Status status = dataItemResult.getStatus();
                        showText(status.isSuccess() ? "发送完成" : "发送失败!" + status.getStatusMessage());
                        Log.i("SendData", status.getStatusMessage());
                        view.setEnabled(true);
                    }
                });
                if (!res) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            view.setEnabled(true);
                        }
                    });
                    showText("发送失败,请检查网络情况");
                }
            }
        }).start();
    }

    private void showText(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CollectOriginalSignalActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
