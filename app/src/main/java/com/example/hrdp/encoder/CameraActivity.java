package com.example.hrdp.encoder;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.evs.encoder.R;

import java.util.concurrent.CountDownLatch;

/**
 * Created by Mansu on 2016-06-12.
 */
public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    public static final int REQUEST_CAMERA=9999;

    public static EncoderService service;
    public static boolean bound = false;
    private CountDownLatch latch;
    private SimpleCamera2 simpleCamera2;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected");
            EncoderService.LocalBinder binder = (EncoderService.LocalBinder)iBinder;
            service = binder.getService();
            bound = true;
            service.start(640, 480);

            latch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisConnected");
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");

        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setContentView(R.layout.video_frame);
        simpleCamera2 = new SimpleCamera2(CameraActivity.this);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");

        super.onStart();
        onbind();
    }

    private void onbind() {
        Log.d(TAG, "onBind");
        //onbind때마다 초기화안시켜주면 문제생기더라.. 앱이 완전히 종료되었다 다시켜져도 down시킬시 down된 상태로 남아있음.
        latch = new CountDownLatch(1);
        Intent intent = new Intent(this, EncoderService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();
        simpleCamera2.readyCamera();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");

        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK)
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "back button clicked");
                finish();
            }
        return true;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");

        super.onStop();
        unbind();
    }

    public void unbind() {
        Log.d(TAG, "unBind");
        if(bound) {
            stopService(new Intent(this, EncoderService.class));
            bound = false;
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");

        super.onDestroy();
        simpleCamera2.closeCamera();
    }
}