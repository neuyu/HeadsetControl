package com.afollestad.materialcamerasample.camera.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import com.afollestad.materialcamerasample.camera.ICallback;
import com.afollestad.materialcamerasample.camera.internal.CameraFragment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by tomiurankar on 06/03/16.
 */
public class ImageUtil {
    /**
     * Saves byte[] array to disk
     *
     * @param input    byte array
     * @param output   path to output file
     * @param callback will always return in originating thread
     */

    public static void saveToDiskAsync(final CameraFragment fragment, final byte[] input, final File output, final ICallback callback) {
        final Handler handler = new Handler(fragment.getActivity().getMainLooper());
        new Thread() {
            @Override
            public void run() {
                try {
                    byte[] resolvedInput = input;
                    if (fragment.isFrontCamera()) {
                        resolvedInput = mirrorImg(input);
                    }
                    FileOutputStream outputStream = new FileOutputStream(output);
                    outputStream.write(resolvedInput);
                    outputStream.flush();
                    outputStream.close();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.done(null);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.done(e);
                        }
                    });
                }
            }
        }.start();
    }

    private static byte[] mirrorImg(byte[] data) {
        Bitmap newImage = null;
        Bitmap cameraBitmap;
        if (data != null) {
            cameraBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            // use matrix to reverse image data and keep it normal
            Matrix mtx = new Matrix();
            //this will prevent mirror effect
            mtx.preScale(-1.0f, 1.0f);
            // Rotating Bitmap , create real image that we want
            newImage = Bitmap.createBitmap(cameraBitmap, 0, 0, cameraBitmap.getWidth(), cameraBitmap.getHeight(), mtx, true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            newImage.compress(Bitmap.CompressFormat.PNG, 50, baos);
            return baos.toByteArray();
        }
        return null;
    }


    /**
     * Helper function for getRotatedBitmap(String, int, int, int)
     *
     * @param inputFile inputFile Expects an JPEG file if corrected orientation wants to be set.
     * @return rotated bitmap or null
     */
    public static Bitmap getRotatedBitmap(String inputFile, int reqWidth, int reqHeight) {
        final int IN_SAMPLE_SIZE_DEFAULT_VAL = 1;
        return getRotatedBitmap(inputFile, reqWidth, reqHeight, IN_SAMPLE_SIZE_DEFAULT_VAL);
    }

    /**
     * Rotates the bitmap per their EXIF flag. This is a recursive function that will
     * be called again if the image needs to be downsized more.
     *
     * @param inputFile Expects an JPEG file if corrected orientation wants to be set.
     * @return rotated bitmap or null
     */
    @Nullable
    private static Bitmap getRotatedBitmap(String inputFile, int reqWidth, int reqHeight, int inSampleSize) {
        final int rotationInDegrees = getExifDegreesFromJpeg(inputFile);

        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(inputFile, opts);
        opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight, inSampleSize);
        opts.inJustDecodeBounds = false;

        final Bitmap origBitmap = BitmapFactory.decodeFile(inputFile, opts);

        if (origBitmap == null)
            return null;

        Matrix matrix = new Matrix();
        matrix.preRotate(rotationInDegrees);
        // we need not check if the rotation is not needed, since the below function will then return the same bitmap. Thus no memory loss occurs.

        try {
            return Bitmap.createBitmap(origBitmap, 0, 0, origBitmap.getWidth(), origBitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            return getRotatedBitmap(inputFile, reqWidth, reqHeight, opts.inSampleSize + 1);
        }
    }

    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight, int inSampleSize) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private static int getExifDegreesFromJpeg(String inputFile) {
        try {
            final ExifInterface exif = new ExifInterface(inputFile);
            final int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
                return 90;
            } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
                return 180;
            } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                return 270;
            }
        } catch (IOException e) {
            Log.e("exif", "Error when trying to get exif data from : " + inputFile, e);
        }
        return 0;
    }
}