package com.afollestad.materialcamerasample.camera;

import android.app.Fragment;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.afollestad.materialcamerasample.MuApplication;
import com.afollestad.materialcamerasample.R;
import com.afollestad.materialcamerasample.camera.internal.BaseCameraFragment;
import com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity;
import com.afollestad.materialcamerasample.camera.internal.CameraFragment;
import com.afollestad.materialcamerasample.camera.internal.PlaybackVideoFragment;
import com.afollestad.materialcamerasample.camera.internal.StillshotPreviewFragment;
import com.afollestad.materialcamerasample.camera.util.CameraUtil;
import com.afollestad.materialcamerasample.camera.util.Constants;
import com.umeng.analytics.MobclickAgent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CaptureActivity extends BaseCaptureActivity implements SensorEventListener {
    private static final String TAG = "CaptureActivity";
    private long keyDownTime;
    private CameraFragment mFragment;
    private int mOrientation = 0;

    private SensorManager sm;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        sm = (SensorManager) this.getSystemService(SENSOR_SERVICE);

        Sensor accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg != null && msg.what == 0) {
                    Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
                    if (fragment instanceof BaseCameraFragment){
                        ((BaseCameraFragment)fragment).setPreviewImg();
                    }
                    if (CameraUtil.isSdCardAvailable()){
                        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + msg.obj)));
                    }
                }
            }
        };
    }

    public Handler getHandler() {
        return mHandler;
    }

    public int getOrientation() {
        return mOrientation;
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("CaptureActivity");
        MobclickAgent.onResume(this);

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.values[0] < 6.5 && event.values[0] > -6.5) {
            mOrientation = Constants.ORIENT_PORTRAIT;
        } else {
            if (event.values[0] > 0) {
                mOrientation = Constants.ORIENT_LANDSCAPE_LEFT;
            } else {
                mOrientation = Constants.ORIENT_LANDSCAPE_RIGHT;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd("CaptureActivity");
        MobclickAgent.onPause(this);
        sm.unregisterListener(this);
    }

    @Override
    @NonNull
    public Fragment getFragment() {
        mFragment = CameraFragment.newInstance();
        return mFragment;
    }

    private Method getMethodIsExternal() {
        try {
            return InputDevice.class.getDeclaredMethod("isExternal");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CaptureActivity setFragment(CameraFragment fragment) {
        mFragment = fragment;
        return this;
    }

    private void takePicture() {
        if (mFragment != null && mFragment.isRecording()) {
            mFragment.takeStillshot();
        } else {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
            if (fragment instanceof StillshotPreviewFragment) {
                ((StillshotPreviewFragment) fragment).useVideo();
                mFragment = ((StillshotPreviewFragment) fragment).gotoCameraFragment();
            } else if (fragment instanceof CameraFragment && mFragment != null) {
                mFragment.takeStillshot();
            } else if (mFragment == null) {

            }
        }
    }

    private void takeVideo() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
        if (useStillshot()) {
            if (fragment instanceof PlaybackVideoFragment) {
                ((PlaybackVideoFragment) fragment).useVideo();
                mFragment = ((PlaybackVideoFragment) fragment).replaceNewVideo();
            } else if (fragment instanceof CameraFragment) {
                mFragment.setRecording(mFragment.startRecordingVideo());
            }
        } else if (mFragment != null && mFragment.isRecording()) {
            mFragment.stopRecordingVideo(false);
        } else if (fragment instanceof PlaybackVideoFragment) {
            ((PlaybackVideoFragment) fragment).useVideo();
            mFragment = ((PlaybackVideoFragment) fragment).replaceNewVideo();
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
                    if (clickTime - keyDownTime > 500) {
                        volumeUpKeyEnter(event.getDevice());
                        keyDownTime = clickTime;
                    }
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (clickTime - keyDownTime > 500) {
                        volumeDownKeyEnter(event.getDevice());
                        keyDownTime = clickTime;
                    }
                    return true;

                case KeyEvent.KEYCODE_3:
                    //切换前后摄像头
                    if (clickTime - keyDownTime > 500) {
                        keycode3Enter();
                        keyDownTime = clickTime;
                    }
                    return true;
                case KeyEvent.KEYCODE_4:
                    if (clickTime - keyDownTime > 500) {
                        keycode4Enter(event.getDevice());
                        keyDownTime = clickTime;
                    }
                    return true;
            }
        } else if (action == KeyEvent.ACTION_UP) {
            long clickTime = System.currentTimeMillis();
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (clickTime - keyDownTime > 500) {
                    keycodeEnter(event.getDevice());
                    keyDownTime = clickTime;
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void keycode3Enter() {
        if (isCameraFragment()) mFragment.exchangeCamera();
    }

    private boolean isCameraFragment() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
        return fragment instanceof CameraFragment;
    }

    private void keycodeEnter(InputDevice device) {
        if (MuApplication.getInstance().isBluetoothConnected()) {
            String deviceName = device.getName();
            if (MuApplication.BLUETOOTH_NAME.equals(deviceName)) {
                takePicture();
            } else if (getMethodIsExternal() != null) {
                try {
                    if ((boolean) getMethodIsExternal().invoke(device)) {
                        takePicture();
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void keycode4Enter(InputDevice device) {
        if (MuApplication.getInstance().isBluetoothConnected()) {
            String deviceName = device.getName();
            if (MuApplication.BLUETOOTH_NAME.equals(deviceName)) {
                takeVideo();
            } else if (getMethodIsExternal() != null) {
                try {
                    if ((boolean) getMethodIsExternal().invoke(device)) {
                        //蓝牙，视频
                        takeVideo();
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void volumeDownKeyEnter(InputDevice device) {
        if (MuApplication.getInstance().isBluetoothConnected()) {
            String deviceName = device.getName();
            if (MuApplication.BLUETOOTH_NAME.equals(deviceName)) {
                if (!isCameraFragment()) return;
                mFragment.getPreviewView().handleZoom(true, false, mFragment.getCamera());
            } else if (getMethodIsExternal() != null) {
                try {
                    if ((boolean) getMethodIsExternal().invoke(device)) {
                        if (!isCameraFragment()) return;
                        mFragment.getPreviewView().handleZoom(true, false, mFragment.getCamera());
                    } else {
                        takeVideo();
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        } else {
            takeVideo();
        }
    }

    private void volumeUpKeyEnter(InputDevice device) {
        if (MuApplication.getInstance().isBluetoothConnected()) {
            String deviceName = device.getName();
            if (MuApplication.BLUETOOTH_NAME.equals(deviceName)) {
                if (!isCameraFragment()) return;
                mFragment.getPreviewView().handleZoom(true, true, mFragment.getCamera());
            } else if (getMethodIsExternal() != null) {
                try {
                    if ((boolean) getMethodIsExternal().invoke(device)) {
                        //蓝牙，放大视距
                        if (!isCameraFragment()) return;
                        mFragment.getPreviewView().handleZoom(true, true, mFragment.getCamera());
                    } else {
                        takePicture();
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        } else {
            takePicture();
        }
    }
}