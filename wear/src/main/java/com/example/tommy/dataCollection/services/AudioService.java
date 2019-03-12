package com.example.tommy.dataCollection.services;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;
import com.example.tommy.dataCollection.utils.WaveHeader;

import java.io.*;

public class AudioService {
    private static final String TAG = "AudioService";

    //音频输入-麦克风
    private final static int AUDIO_INPUT = MediaRecorder.AudioSource.MIC;
    //采用频率
    //44100是目前的标准，但是某些设备仍然支持22050，16000，11025
    //采样频率一般共分为22.05KHz、44.1KHz、48KHz三个等级
    private final static int AUDIO_SAMPLE_RATE = 44100;
    //声道 单声道
    private final static int AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_STEREO;
    //编码
    private final static int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    // 缓冲区字节大小
    private int bufferSizeInBytes = 0;

    //录音对象
    private AudioRecord audioRecord;

    // 保存录音信息的文件
    private File file;

    boolean isRecording;
    boolean stopped;

    public AudioService() {
        // 获得缓冲区字节大小
        bufferSizeInBytes = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL, AUDIO_ENCODING);

        try {
            this.file = File.createTempFile("record", null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startRecord() {
        if (isRecording) return;

        isRecording = true;

        audioRecord = new AudioRecord(AUDIO_INPUT, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_ENCODING, bufferSizeInBytes);
        audioRecord.startRecording();

        new Thread(new Runnable() {
            @Override
            public void run() {
                readAndWriteData();
            }
        }).start();
    }

    private void readAndWriteData() {
        // new一个byte数组用来存一些字节数据，大小为缓冲区大小
        byte[] audiodata = new byte[bufferSizeInBytes];

        int count;

        try {
            FileOutputStream fos = new FileOutputStream(this.file);

            stopped = false;
            while (isRecording) {
                count = audioRecord.read(audiodata, 0, bufferSizeInBytes);
                if (AudioRecord.ERROR_INVALID_OPERATION != count) {
                    fos.write(audiodata);
                }
            }

            stopped = true;
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public File stopRecrod() {
        if (isRecording) {
            isRecording = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        // 等待写进程退出
        while (!stopped) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        writeWavHeader(this.file);

        return this.file;
    }

    private void writeWavHeader(File pcm) {
        int TOTAL_SIZE = (int) pcm.length();

        WaveHeader header = new WaveHeader();
        // 长度字段 = 内容的大小（TOTAL_SIZE) +
        // 头部字段的大小(不包括前面4字节的标识符RIFF以及fileLength本身的4字节)
        header.fileLength = TOTAL_SIZE + (44 - 8);
        header.FmtHdrLeth = 16;
        header.BitsPerSample = 16;
        header.Channels = 1;
        header.FormatTag = 0x0001;
        header.SamplesPerSec = AUDIO_SAMPLE_RATE;
        header.BlockAlign = (short) (header.Channels * header.BitsPerSample / 8);
        header.AvgBytesPerSec = header.BlockAlign * header.SamplesPerSec;
        header.DataHdrLeth = TOTAL_SIZE;

        //合成所有的pcm文件的数据，写到目标文件
        try {
            byte[] h = header.getHeader();

            File tmp = File.createTempFile("tmp",null);
            tmp.deleteOnExit();
            RandomAccessFile raf = new RandomAccessFile(pcm, "rw");

            // 保存原文件
            FileOutputStream tos = new FileOutputStream(tmp);
            FileInputStream tis = new FileInputStream(tmp);
            raf.seek(0);
            byte[] buf = new byte[64];
            int hasRead = 0;
            while((hasRead = raf.read(buf)) > 0) {
                tos.write(buf, 0, hasRead);
            }

            // 写 wav 头
            raf.seek(0);
            raf.write(h);

            // 写回原文件
            while((hasRead = tis.read(buf)) > 0){
                raf.write(buf, 0, hasRead);
            }

            tos.close();
            tis.close();
            raf.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
