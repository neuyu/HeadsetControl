package com.afollestad.materialcamerasample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.afollestad.materialcamerasample.camera.MaterialCamera;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;

import hugo.weaving.DebugLog;

/**
 * @author Aidan Follestad (afollestad)
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final static int PERMISSION_RQ = 84;
    private final static int PERMISSION_BT = 85;

    private Button launchCameraButton;
    private Button launchCameraStillshotButton;


    private long keyDownTime;

    private Method mMethodisExternal;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        launchCameraButton = (Button) findViewById(R.id.launchCamera);
        launchCameraButton.setOnClickListener(this);
        launchCameraStillshotButton = (Button) findViewById(R.id.launchCameraStillshot);
        launchCameraStillshotButton.setOnClickListener(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Request permission to save videos in external storage
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_RQ);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            // Request permission to save videos in external storage
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, PERMISSION_BT);
        }

        try {
            mMethodisExternal = InputDevice.class.getDeclaredMethod("isExternal");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        int action = event.getAction();
        int keyCode = event.getKeyCode();

        if (action == KeyEvent.ACTION_DOWN) {
            long clickTime = System.currentTimeMillis();
            switch (keyCode) {

                case KeyEvent.KEYCODE_VOLUME_UP:
                    if (clickTime - keyDownTime > 1000) {

                        volumeUpKeyEnter(event.getDevice());

                        keyDownTime = clickTime;
                    }
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (clickTime - keyDownTime > 1000) {

                        volumeDownKeyEnter(event.getDevice());
                        keyDownTime = clickTime;
                    }
                    return true;

                case KeyEvent.KEYCODE_4:
                    if (clickTime - keyDownTime > 1000) {
                        keycode4Enter(event.getDevice());
                        keyDownTime = clickTime;
                    }
                    return true;
            }
        }else if (action == KeyEvent.ACTION_UP){
            long clickTime = System.currentTimeMillis();
            if (keyCode == KeyEvent.KEYCODE_ENTER){
                if (clickTime - keyDownTime > 1000) {
                    keycodeEnter(event.getDevice());
                    keyDownTime = clickTime;
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 蓝牙的摄像按钮
     * @param device
     */
    @DebugLog
    private void keycodeEnter(InputDevice device) {
        if (MuApplication.getInstance().isBluetoothConnected()) {
            String deviceName = device.getName();
            if (MuApplication.BLUETOOTH_NAME.equals(deviceName)) {
                onClick(launchCameraStillshotButton);
            } else if (mMethodisExternal != null) {
                try {
                    if ((boolean) mMethodisExternal.invoke(device)) {
                        onClick(launchCameraStillshotButton);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 蓝牙的视频按键键入
     *
     * @param device
     */
    private void keycode4Enter(InputDevice device) {
        if (MuApplication.getInstance().isBluetoothConnected()) {
            String deviceName = device.getName();
            if (MuApplication.BLUETOOTH_NAME.equals(deviceName)) {
                onClick(launchCameraButton);
            } else if (mMethodisExternal != null) {
                try {
                    if ((boolean) mMethodisExternal.invoke(device)) {
                        //蓝牙，视频
                        onClick(launchCameraButton);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 音量-键入
     *
     * @param device
     */
    private void volumeDownKeyEnter(InputDevice device) {
        if (MuApplication.getInstance().isBluetoothConnected()) {
            if (mMethodisExternal != null) {
                try {
                    if (!(boolean) mMethodisExternal.invoke(device)) {
                        onClick(launchCameraButton);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        } else {
            onClick(launchCameraButton);
        }
    }

    /**
     * 蓝牙或手机音量+键入
     */
    @DebugLog
    private void volumeUpKeyEnter(InputDevice device) {
        if (MuApplication.getInstance().isBluetoothConnected()) {
            if (mMethodisExternal != null) {
                try {
                    if (!(boolean) mMethodisExternal.invoke(device)) {
                        onClick(launchCameraStillshotButton);
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        } else {
            onClick(launchCameraStillshotButton);
        }
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void onClick(View view) {

        MaterialCamera materialCamera = new MaterialCamera(this)
                .showPortraitWarning(false)
                .allowRetry(true)
                .retryExits(true)
                .defaultToFrontFacing(false)
                .autoSubmit(false)
                .labelConfirm(R.string.mcam_use_video);

        if (view.getId() == R.id.launchCameraStillshot)
            materialCamera
                    .stillShot() // launches the Camera in stillshot mode
                    .labelConfirm(R.string.mcam_use_stillshot);
        materialCamera.start();
    }

    private String readableFileSize(long size) {
        if (size <= 0) return size + " B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.##").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private String fileSize(File file) {
        return readableFileSize(file.length());
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            // Sample was denied WRITE_EXTERNAL_STORAGE permission
            Toast.makeText(this, "Videos will be saved in a cache directory instead of an external storage directory since permission was denied.", Toast.LENGTH_LONG).show();
        }
        if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            // Sample was denied WRITE_EXTERNAL_STORAGE permission
            Toast.makeText(this, "this app should have BLUETOOTH's permission", Toast.LENGTH_LONG).show();
        }
    }
}