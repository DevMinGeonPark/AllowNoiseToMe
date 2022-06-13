package com.dsu.android.project.allownoisetome;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static String PARCEL_KEY_LOUDNESS = "parcel key loudness";
    private static final int RC_AUDIO_RECORD = 1;
    private static final String TAG = "MainActivity";
    private static final int SAMPLE_RATE = 44100;
    private static final double AVERAGE_VAL = 20d;

    //btns
    private Button btnRecord;
    private Button btnStop;

    private TextView dBText;

    private Handler bgThreadHandler;
    private Handler uiThreadHandler;
    private HandlerThread bgThread;
    private AudioRecord audioRecord;
    private boolean isRecording;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRecord = findViewById(R.id.btn_record);
        btnStop = findViewById(R.id.btn_stop);
        dBText = findViewById(R.id.dBText);

        btnRecord.setOnClickListener(v -> {
            if (isRecordingAllowed()) {
                startRecording();
            } else {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RC_AUDIO_RECORD);
            }
        });
        btnStop.setOnClickListener(v -> stopRecording());
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiThreadHandler = null;
    }

    private boolean isRecordingAllowed() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RC_AUDIO_RECORD) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                startRecording();
            else
                Toast.makeText(this, "Permission denied by user", Toast.LENGTH_SHORT).show();
        }
    }

    private void startBgThread() {
        bgThread = new HandlerThread("background thread");
        bgThread.start();
        bgThreadHandler = new Handler(bgThread.getLooper());
        uiThreadHandler = new UIThreadHandler(dBText);
        Log.i(TAG, "background thread started");
    }

    private void stopBgThread() {
        Log.i(TAG, "Stopping bg thread");
        if (bgThread != null) {
            try {
                bgThread.quitSafely();
                bgThread.join();
                bgThread = null;
                bgThreadHandler = null;
                uiThreadHandler = null;
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
        Log.i(TAG, "Background Thread stopped");
    }

    private void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            return;
        audioRecord = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build())
                .build();
        audioRecord.startRecording();
        isRecording=true;
        startBgThread();
        bgThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                int minBufSize = AudioRecord.getMinBufferSize(32000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
                short[] buffer = new short[2*minBufSize];
                Bundle bundle = new Bundle();
                while (isRecording) {
                    audioRecord.read(buffer, 0, 2 * minBufSize);
                    double sqrsum = 0.0;
                    for (int i = 0; i < 2 * minBufSize; ++i) {
                        sqrsum += Math.pow(buffer[i],2);
                    }
                    double rmsamp = Math.sqrt((sqrsum/2*minBufSize));
                    double loudness;
                    if(rmsamp>0) {
                        loudness = 10*Math.log10(rmsamp / AVERAGE_VAL);
                    } else {
                        loudness = 1d;
                    }
                    bundle.putDouble(PARCEL_KEY_LOUDNESS,loudness);
                    Message message = new Message();
                    message.setData(bundle);
                    uiThreadHandler.sendMessage(message);
                }
                Log.i(TAG,"Recording stopped");
            }
        });
    }

    private void stopRecording() {
        if(audioRecord!=null) {
            audioRecord.stop();
            audioRecord = null;
            isRecording = false;
            stopBgThread();
        }
    }

    static class UIThreadHandler extends Handler {
        TextView dBText;
        UIThreadHandler(TextView textView) {
            dBText = textView;
        }
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            Log.i(TAG,"handle message of UIThreadHandler");
            Bundle bundle = msg.getData();
            double loudness = bundle.getDouble(PARCEL_KEY_LOUDNESS);
            this.dBText.setText(Math.round(loudness)+"");
        }
    }
}
