package com.afollestad.materialcamerasample.camera;

import android.graphics.Bitmap;

public interface ICallback {
    /**
     * It is called when the background operation completes. If the operation is successful, {@code
     * exception} will be {@code null}.
     */
    void done(Bitmap bitmap, Exception exception);
}