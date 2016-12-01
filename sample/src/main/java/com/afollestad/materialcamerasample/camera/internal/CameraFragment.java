package com.afollestad.materialcamerasample.camera.internal;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.afollestad.materialcamerasample.R;
import com.afollestad.materialcamerasample.camera.CaptureActivity;
import com.afollestad.materialcamerasample.camera.ICallback;
import com.afollestad.materialcamerasample.camera.util.CameraUtil;
import com.afollestad.materialcamerasample.camera.util.Constants;
import com.afollestad.materialcamerasample.camera.util.Degrees;
import com.afollestad.materialcamerasample.camera.util.ImageUtil;
import com.afollestad.materialcamerasample.camera.util.ManufacturerUtil;
import com.umeng.analytics.MobclickAgent;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity.CAMERA_POSITION_FRONT;
import static com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity.FLASH_MODE_ALWAYS_ON;
import static com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity.FLASH_MODE_AUTO;
import static com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity.FLASH_MODE_OFF;

/**
 * @author Aidan Follestad (afollestad)
 */
@SuppressWarnings("deprecation")
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class CameraFragment extends BaseCameraFragment implements View.OnClickListener {

    private CameraPreview mPreviewView;
    FrameLayout mPreviewFrame;
    private static final float RATIO_TOLERANCE = 0.1f;
    private Camera.Size mVideoSize;
    private Camera mCamera;
    private Point mWindowSize;
    private int mDisplayOrientation;
    private boolean mIsAutoFocusing;
    private boolean mFrontCamera;
    private static int mInitVideoRotation;


    public static CameraFragment newInstance() {
        CameraFragment fragment = new CameraFragment();
        //fragment.setRetainInstance(true);
        return fragment;
    }

    private static Camera.Size chooseVideoSize(BaseCaptureInterface ci, List<Camera.Size> choices) {
        for (Camera.Size size : choices) {
            if (size.height == ci.videoPreferredHeight()) {
                return size;
            }
        }
        LOG(CameraFragment.class, "Couldn't find any suitable video size");
        return choices.get(choices.size() - 1);
    }

    private static Camera.Size chooseOptimalSize(List<Camera.Size> choices, int width, int height, Camera.Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Camera.Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.width;
        int h = aspectRatio.height;
        for (Camera.Size option : choices) {
            if (option.height == width * h / w &&
                    option.width >= width && option.height >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            LOG(CameraFragment.class, "Couldn't find any suitable preview size");
            return aspectRatio;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mPreviewFrame = (FrameLayout) view.findViewById(R.id.rootFrame);

        mPreviewFrame.setOnClickListener(this);
        ((CaptureActivity) getActivity()).setFragment(this);
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            mPreviewView.getHolder().getSurface().release();
        } catch (Throwable ignored) {
        }
        mPreviewFrame = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("CameraFragment");
        openCamera();
    }

    @Override
    public void onPause() {
        if (mCamera != null) mCamera.lock();
        MobclickAgent.onPageEnd("CameraFragment");
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.rootFrame) {
            if (mCamera == null || mIsAutoFocusing) return;
            try {
                mIsAutoFocusing = true;
                mCamera.cancelAutoFocus();
                mCamera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        mIsAutoFocusing = false;
                        if (!success)
                            Toast.makeText(getActivity(), "Unable to auto-focus!", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            super.onClick(view);
        }
    }

    @Override
    public void openCamera() {
        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) return;
        try {
            final int mBackCameraId = mInterface.getBackCamera() != null ? (Integer) mInterface.getBackCamera() : -1;
            final int mFrontCameraId = mInterface.getFrontCamera() != null ? (Integer) mInterface.getFrontCamera() : -1;
            if (mBackCameraId == -1 || mFrontCameraId == -1) {
                int numberOfCameras = Camera.getNumberOfCameras();
                if (numberOfCameras == 0) {
                    throwError(new Exception("No cameras are available on this device."));
                    return;
                }

                for (int i = 0; i < numberOfCameras; i++) {
                    //noinspection ConstantConditions
                    if (mFrontCameraId != -1 && mBackCameraId != -1) break;
                    Camera.CameraInfo info = new Camera.CameraInfo();
                    Camera.getCameraInfo(i, info);
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && mFrontCameraId == -1) {
                        mInterface.setFrontCamera(i);
                    } else if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK && mBackCameraId == -1) {
                        mInterface.setBackCamera(i);
                    }
                }
            }

            if (mWindowSize == null)
                mWindowSize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(mWindowSize);
            final int toOpen = getCurrentCameraId();
            mFrontCamera = !(toOpen == -1 || toOpen == 0);
            mCamera = Camera.open(toOpen == -1 ? 0 : toOpen);


            Camera.Parameters parameters = mCamera.getParameters();
            List<Camera.Size> videoSizes = parameters.getSupportedVideoSizes();
            if (videoSizes == null || videoSizes.size() == 0)
                videoSizes = parameters.getSupportedPreviewSizes();
            mVideoSize = chooseVideoSize((BaseCaptureActivity) activity, videoSizes);
            Camera.Size previewSize = chooseOptimalSize(parameters.getSupportedPreviewSizes(),
                    mWindowSize.x, mWindowSize.y, mVideoSize);

            if (ManufacturerUtil.isSamsungGalaxyS3()) {
                parameters.setPreviewSize(ManufacturerUtil.SAMSUNG_S3_PREVIEW_WIDTH,
                        ManufacturerUtil.SAMSUNG_S3_PREVIEW_HEIGHT);
            } else {
                parameters.setPreviewSize(previewSize.width, previewSize.height);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                    parameters.setRecordingHint(true);
            }
            //setupFlashMode(); 该方法获取的parameters和openCamera方法内的不一致
            if (mFrontCamera) {
                mButtonFlash.setVisibility(View.INVISIBLE);
            } else {
                mButtonFlash.setVisibility(View.VISIBLE);
            }
            if (mInterface.getFlashMode() == FLASH_MODE_ALWAYS_ON) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
            } else {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }
            //Camera.Size mStillShotSize = getHighestSupportedStillShotSize(parameters.getSupportedPictureSizes());
            parameters.setPictureSize(previewSize.width, previewSize.height);
            setCameraDisplayOrientation(parameters);
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            mCamera.setParameters(parameters);
            createPreview();
            mMediaRecorder = new MediaRecorder();

            onCameraOpened();
        } catch (IllegalStateException e) {
            throwError(new Exception("Cannot access the camera.", e));
        } catch (RuntimeException e2) {
            throwError(new Exception("Cannot access the camera, you may need to restart your device.", e2));
        }
    }

    private Camera.Size getHighestSupportedStillShotSize(List<Camera.Size> supportedPictureSizes) {
        Collections.sort(supportedPictureSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size lhs, Camera.Size rhs) {
                if (lhs.height * lhs.width > rhs.height * rhs.width)
                    return -1;
                return 1;

            }
        });
        Camera.Size maxSize = supportedPictureSizes.get(0);
        Log.d("CameraFragment", "Using resolution: " + maxSize.width + "x" + maxSize.height);
        return maxSize;
    }

    @SuppressWarnings("WrongConstant")
    private void setCameraDisplayOrientation(Camera.Parameters parameters) {
        Camera.CameraInfo info =
                new Camera.CameraInfo();
        Camera.getCameraInfo(getCurrentCameraId(), info);
        final int deviceOrientation = Degrees.getDisplayRotation(getActivity());
        mDisplayOrientation = Degrees.getDisplayOrientation(
                info.orientation, deviceOrientation, info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        Log.d("CameraFragment", String.format("Orientations: Sensor = %d˚, Device = %d˚, Display = %d˚",
                info.orientation, deviceOrientation, mDisplayOrientation));

        int previewOrientation;
        int jpegOrientation;
        if (CameraUtil.isChromium()) {
            previewOrientation = 0;
            jpegOrientation = 0;
        } else {
            jpegOrientation = previewOrientation = mDisplayOrientation;

            if (Degrees.isPortrait(deviceOrientation) && getCurrentCameraPosition() == CAMERA_POSITION_FRONT)
                previewOrientation = Degrees.mirror(mDisplayOrientation);
        }

        parameters.setRotation(jpegOrientation);
        mCamera.setDisplayOrientation(previewOrientation);
    }

    private void createPreview() {
        Activity activity = getActivity();
        if (activity == null) return;
        if (mWindowSize == null)
            mWindowSize = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(mWindowSize);
        mPreviewView = new CameraPreview(getActivity(), mCamera);
        if (mPreviewFrame.getChildCount() > 0 && mPreviewFrame.getChildAt(0) instanceof CameraPreview)
            mPreviewFrame.removeViewAt(0);
        mPreviewFrame.addView(mPreviewView, 0);
        mPreviewView.setAspectRatio(mWindowSize.x, mWindowSize.y);
    }


    @Override
    public void closeCamera() {
        try {
            if (mCamera != null) {
                try {
                    mCamera.lock();
                } catch (Throwable ignored) {
                }
                mCamera.release();
                mCamera = null;
            }
        } catch (IllegalStateException e) {
            throwError(new Exception("Illegal state while trying to close camera.", e));
        }
    }


    private boolean prepareMediaRecorder() {
        try {
            final Activity activity = getActivity();
            if (null == activity) return false;
            final BaseCaptureInterface captureInterface = (BaseCaptureInterface) activity;

            setCameraDisplayOrientation(mCamera.getParameters());
            mMediaRecorder = new MediaRecorder();
            mCamera.stopPreview();
            mCamera.unlock();
            mMediaRecorder.setCamera(mCamera);

            boolean canUseAudio = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                canUseAudio = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

            if (canUseAudio) {
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            } else {
                Toast.makeText(getActivity(), R.string.mcam_no_audio_access, Toast.LENGTH_LONG).show();
            }
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);

            final CamcorderProfile profile = CamcorderProfile.get(getCurrentCameraId(), mInterface.qualityProfile());
            mMediaRecorder.setOutputFormat(profile.fileFormat);
            mMediaRecorder.setVideoFrameRate(mInterface.videoFrameRate(profile.videoFrameRate));
            mMediaRecorder.setVideoSize(mVideoSize.width, mVideoSize.height);
            mMediaRecorder.setVideoEncodingBitRate(mInterface.videoEncodingBitRate(profile.videoBitRate));
            mMediaRecorder.setVideoEncoder(profile.videoCodec);

            if (canUseAudio) {
                mMediaRecorder.setAudioEncodingBitRate(mInterface.audioEncodingBitRate(profile.audioBitRate));
                mMediaRecorder.setAudioChannels(profile.audioChannels);
                mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);
                mMediaRecorder.setAudioEncoder(profile.audioCodec);
            }

            Uri uri = Uri.fromFile(getOutputMediaFile());
            mOutputUri = uri.toString();
            mMediaRecorder.setOutputFile(uri.getPath());

            if (captureInterface.maxAllowedFileSize() > 0) {
                mMediaRecorder.setMaxFileSize(captureInterface.maxAllowedFileSize());
                mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                    @Override
                    public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                            Toast.makeText(getActivity(), R.string.mcam_file_size_limit_reached, Toast.LENGTH_SHORT).show();
                            stopRecordingVideo(false);
                        }
                    }
                });
            }

            mMediaRecorder.setOrientationHint(mDisplayOrientation);
            mMediaRecorder.setPreviewDisplay(mPreviewView.getHolder().getSurface());


            int rotation = getFinalRotation();
            mInitVideoRotation = rotation;
            mMediaRecorder.setOrientationHint(rotation);
            try {
                mMediaRecorder.prepare();
                return true;
            } catch (Throwable e) {
                throwError(new Exception("Failed to prepare the media recorder: " + e.getMessage(), e));
                return false;
            }
        } catch (Throwable t) {
            try {
                mCamera.lock();
            } catch (IllegalStateException e) {
                throwError(new Exception("Failed to re-lock camera: " + e.getMessage(), e));
                return false;
            }
            t.printStackTrace();
            throwError(new Exception("Failed to begin recording: " + t.getMessage(), t));
            return false;
        }
    }

    private int getFinalRotation() {
        int rotation = getMediaRotation(getCurrentCameraId());
        rotation += compensateDeviceRotation();
        return rotation % 360;
    }

    @Override
    public boolean startRecordingVideo() {
        super.startRecordingVideo();

        if (mInitVideoRotation != getFinalRotation()) {
            releaseRecorder();
        }
        if (prepareMediaRecorder()) {
            try {
                // UI
                setImageRes(mButtonVideo, mInterface.iconStop());
                if (!CameraUtil.isChromium())
                    mButtonFacing.setVisibility(View.INVISIBLE);

                // Only start counter if count down wasn't already started
                mInterface.setRecordingStart(System.currentTimeMillis());
                startCounter();

                // Start recording
                mMediaRecorder.start();

                mButtonVideo.setEnabled(false);
                mButtonVideo.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mButtonVideo.setEnabled(true);
                    }
                }, 200);

                return true;
            } catch (Throwable t) {
                t.printStackTrace();
                mInterface.setRecordingStart(-1);
                stopRecordingVideo(false);
                throwError(new Exception("Failed to start recording: " + t.getMessage(), t));
            }
        }
        return false;
    }

    @Override
    public void stopRecordingVideo(final boolean reachedZero) {
        super.stopRecordingVideo(reachedZero);

        if (mCamera != null)
            mCamera.lock();
        releaseRecorder();
        closeCamera();

        if (!mInterface.didRecord())
            mOutputUri = null;

        setImageRes(mButtonVideo, mInterface.iconRecord());
        if (!CameraUtil.isChromium())
            mButtonFacing.setVisibility(View.VISIBLE);
        if (mInterface.getRecordingStart() > -1 && getActivity() != null)
            mInterface.onShowPreview(mOutputUri, reachedZero);

        stopCounter();
    }

    private void setupFlashMode() {
        String flashMode = null;
        switch (mInterface.getFlashMode()) {
            case FLASH_MODE_AUTO:
                flashMode = Camera.Parameters.FLASH_MODE_AUTO;
                break;
            case FLASH_MODE_ALWAYS_ON:
                flashMode = Camera.Parameters.FLASH_MODE_ON;
                break;
            case FLASH_MODE_OFF:
                flashMode = Camera.Parameters.FLASH_MODE_OFF;
                break;
        }
        if (flashMode != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setFlashMode(flashMode);
            mCamera.setParameters(parameters);
        }
    }

    @Override
    public void onPreferencesUpdated() {
        setupFlashMode();
    }

    @Override
    public void takeStillshot() {

        Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
            public void onPictureTaken(byte[] data, Camera camera) {
                final File outputPic = getOutputPictureFile();
                // lets save the image to disk
                ImageUtil.saveToDiskAsync(CameraFragment.this, data, outputPic, new ICallback() {
                    @Override
                    public void done(Bitmap bitmap, Exception e) {
                        if (e == null) {
                            mOutputUri = outputPic.getAbsolutePath();
                            mButtonStillshot.setEnabled(true);
                            if (bitmap == null) {
                                mInterface.onShowStillshot(mOutputUri);
                            } else {
                                mInterface.onShowStillshot(bitmap, mOutputUri);
                            }
                        } else {
                            throwError(e);
                        }
                    }
                });
            }
        };

        mButtonStillshot.setEnabled(false);

        Camera.Parameters mParameters = mCamera.getParameters();
        int rotation = getMediaRotation(getCurrentCameraId());
        rotation += compensateDeviceRotation();
        mParameters.setRotation(rotation % 360);
        mCamera.setParameters(mParameters);
        mCamera.takePicture(null, null, jpegCallback);
    }

    private int getMediaRotation(int currentCameraPosition) {
        final int degrees = getRotationDegrees();
        final Camera.CameraInfo info = CameraUtil.getCameraInfo(currentCameraPosition);
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 + info.orientation + degrees) % 360;
        }

        return (360 + info.orientation - degrees) % 360;
    }


    private int getRotationDegrees() {
        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return 0;
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            default:
                return 0;
        }
    }

    private int compensateDeviceRotation() {
        int degrees = 0;
        boolean isFrontCamera = (getCurrentCameraId() == Camera.CameraInfo.CAMERA_FACING_FRONT);
        int deviceOrientation = ((CaptureActivity) getActivity()).getOrientation();
        if (deviceOrientation == Constants.ORIENT_LANDSCAPE_LEFT) {
            degrees += isFrontCamera ? 90 : 270;
        } else if (deviceOrientation == Constants.ORIENT_LANDSCAPE_RIGHT) {
            degrees += isFrontCamera ? 270 : 90;
        }
        return degrees;
    }

    public CameraPreview getPreviewView() {
        return mPreviewView;
    }

    public Camera getCamera() {
        return mCamera;
    }


    public boolean isFrontCamera() {
        return mFrontCamera;
    }

    static class CompareSizesByArea implements Comparator<Camera.Size> {
        @Override
        public int compare(Camera.Size lhs, Camera.Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.width * lhs.height -
                    (long) rhs.width * rhs.height);
        }
    }

    private static class SizesComparator implements Comparator<Camera.Size>, Serializable {
        private static final long serialVersionUID = 5431278455314658485L;

        @Override
        public int compare(final Camera.Size a, final Camera.Size b) {
            return b.width * b.height - a.width * a.height;
        }
    }
}