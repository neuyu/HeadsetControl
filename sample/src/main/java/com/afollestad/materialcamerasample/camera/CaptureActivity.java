package com.afollestad.materialcamerasample.camera;

import android.app.Fragment;
import android.support.annotation.NonNull;
import android.view.InputDevice;
import android.view.KeyEvent;

import com.afollestad.materialcamerasample.MuApplication;
import com.afollestad.materialcamerasample.R;
import com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity;
import com.afollestad.materialcamerasample.camera.internal.CameraFragment;
import com.afollestad.materialcamerasample.camera.internal.PlaybackVideoFragment;
import com.afollestad.materialcamerasample.camera.internal.StillshotPreviewFragment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CaptureActivity extends BaseCaptureActivity {
    private long keyDownTime;
    private CameraFragment mFragment;
    private Method mMethodisExternal;


    @Override
    @NonNull
    public Fragment getFragment() {
        mFragment = CameraFragment.newInstance();
        try {
            mMethodisExternal = InputDevice.class.getDeclaredMethod("isExternal");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return mFragment;
    }


    private void takePicture() {

        if (useStillshot()) {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
            if (fragment instanceof StillshotPreviewFragment) {
                ((StillshotPreviewFragment) fragment).useVideo();
                finish();
            } else if (fragment instanceof CameraFragment) {
                mFragment.takeStillshot();
            }
        } else {
            finish();
        }
    }

    private void takeVideo() {
        if (useStillshot()) {
            finish();
        } else if (mFragment.isRecording()) {
            mFragment.stopRecordingVideo(false);
        } else {
            Fragment fragment = getFragmentManager().findFragmentById(R.id.container);
            if (fragment instanceof PlaybackVideoFragment) {
                ((PlaybackVideoFragment) fragment).useVideo();
                finish();
            } else if (fragment instanceof CameraFragment) {
                mFragment.setRecording(mFragment.startRecordingVideo());
            }
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
                    mFragment.exchangeCamera();
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

    private void keycodeEnter(InputDevice device) {
        if (MuApplication.getInstance().isBluetoothConnected()) {
            String deviceName = device.getName();
            if (MuApplication.BLUETOOTH_NAME.equals(deviceName)) {
                takePicture();
            } else if (mMethodisExternal != null) {
                try {
                    if ((boolean) mMethodisExternal.invoke(device)) {
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
            } else if (mMethodisExternal != null) {
                try {
                    if ((boolean) mMethodisExternal.invoke(device)) {
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
                mFragment.getPreviewView().handleZoom(true, false, mFragment.getCamera());
            } else if (mMethodisExternal != null) {
                try {
                    if ((boolean) mMethodisExternal.invoke(device)) {
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
                mFragment.getPreviewView().handleZoom(true, true, mFragment.getCamera());
            } else if (mMethodisExternal != null) {
                try {
                    if ((boolean) mMethodisExternal.invoke(device)) {
                        //蓝牙，放大视距
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