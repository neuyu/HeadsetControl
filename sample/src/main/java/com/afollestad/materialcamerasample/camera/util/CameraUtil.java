package com.afollestad.materialcamerasample.camera.util;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.ColorInt;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * @author Aidan Follestad (afollestad)
 */
public class CameraUtil {

    private CameraUtil() {
    }

    public static Camera.CameraInfo getCameraInfo(int cameraId) {
        final Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        return info;
    }

    public static boolean isChromium() {
        return Build.BRAND.equalsIgnoreCase("chromium") &&
                Build.MANUFACTURER.equalsIgnoreCase("chromium");
    }

    public static String getDurationString(long durationMs) {
        return String.format(Locale.getDefault(), "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(durationMs),
                TimeUnit.MILLISECONDS.toSeconds(durationMs) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(durationMs))
        );
    }

    @SuppressWarnings({"ConstantConditions", "ResultOfMethodCallIgnored"})
    public static File makePictureFile(Activity activity, String extension) {

        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        if (isSdCardAvailable()) {
            directory = new File(directory.getAbsolutePath() + File.separator + "Camera");
            directory.mkdirs();
        }else {
            directory = activity.getFilesDir();
        }

        final String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());

        return new File(directory, timeStamp + extension);
    }

    public static boolean isSdCardAvailable(){
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }


    @ColorInt
    public static int darkenColor(@ColorInt int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f; // value component
        color = Color.HSVToColor(hsv);
        return color;
    }

    public static boolean isColorDark(int color) {
        double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return darkness >= 0.5;
    }

}