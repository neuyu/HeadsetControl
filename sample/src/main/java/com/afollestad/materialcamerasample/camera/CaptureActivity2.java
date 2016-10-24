package com.afollestad.materialcamerasample.camera;

import android.app.Fragment;
import android.support.annotation.NonNull;
import android.view.KeyEvent;

import com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity;
import com.afollestad.materialcamerasample.camera.internal.Camera2Fragment;

public class CaptureActivity2 extends BaseCaptureActivity {

    private Camera2Fragment mFragment;
    @Override
    @NonNull
    public Fragment getFragment() {
        mFragment = Camera2Fragment.newInstance();
        return mFragment;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                //take video
                if (useStillshot()){
                    finish();
                }else {
                    mFragment.startRecordingVideo();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (useStillshot()){
                    mFragment.takeStillshot();
                }else {
                    finish();
                    mFragment.takeStillshot();
                }
                //take picture
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}