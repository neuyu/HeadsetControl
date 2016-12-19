package com.afollestad.materialcamerasample.camera.internal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.AppCompatDrawableManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialcamerasample.R;
import com.afollestad.materialcamerasample.camera.MaterialCamera;
import com.afollestad.materialcamerasample.camera.util.CameraUtil;
import com.afollestad.materialcamerasample.camera.util.Degrees;
import com.afollestad.materialcamerasample.camera.util.GlideUtil;
import com.afollestad.materialcamerasample.camera.util.JPGFilenameFilter;
import com.afollestad.materialcamerasample.camera.util.VideoFilenameFilter;
import com.bumptech.glide.Glide;

import java.io.File;

import static android.app.Activity.RESULT_CANCELED;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class BaseCameraFragment extends Fragment implements CameraUriInterface, View.OnClickListener {

    public static final String PREVIEW_IMG_KEY = "IMG";
    public static final String PREVIEW_VIDEO_KEY = "VIDEO";

    protected ImageView mButtonVideo;
    protected ImageView mButtonStillshot;
    protected ImageView mButtonFacing;
    protected ImageView mButtonFlash;
    protected TextView mRecordDuration;
    protected TextView mDelayStartCountdown;
    protected ImageView mViewPreview;

    private boolean mIsRecording;
    protected String mOutputUri;
    protected BaseCaptureInterface mInterface;
    protected Handler mPositionHandler;
    protected MediaRecorder mMediaRecorder;

    private String mImgSrcPath;

    protected static void LOG(Object context, String message) {
        Log.d(context instanceof Class<?> ? ((Class<?>) context).getSimpleName() :
                context.getClass().getSimpleName(), message);
    }

    private final Runnable mPositionUpdater = new Runnable() {
        @Override
        public void run() {
            if (mInterface == null || mRecordDuration == null) return;
            final long mRecordStart = mInterface.getRecordingStart();
            final long mRecordEnd = mInterface.getRecordingEnd();
            if (mRecordStart == -1 && mRecordEnd == -1) return;
            final long now = System.currentTimeMillis();
            if (mRecordEnd != -1) {
                if (now >= mRecordEnd) {
                    stopRecordingVideo(true);
                } else {
                    final long diff = mRecordEnd - now;
                    mRecordDuration.setText(String.format("-%s", CameraUtil.getDurationString(diff)));
                }
            } else {
                mRecordDuration.setText(CameraUtil.getDurationString(now - mRecordStart));
            }
            if (mPositionHandler != null)
                mPositionHandler.postDelayed(this, 1000);
        }
    };

    @Override
    public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        View view = inflater.inflate(R.layout.mcam_fragment_videocapture, container, false);
        return view;
    }

    protected void setImageRes(ImageView iv, @DrawableRes int res) {
        Drawable d = AppCompatDrawableManager.get().getDrawable(iv.getContext(), res);
        d = DrawableCompat.wrap(d.mutate());
        iv.setImageDrawable(d);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewPreview = (ImageView) view.findViewById(R.id.imagePreview);
        mDelayStartCountdown = (TextView) view.findViewById(R.id.delayStartCountdown);
        mButtonVideo = (ImageView) view.findViewById(R.id.video);
        mButtonStillshot = (ImageView) view.findViewById(R.id.stillshot);
        mButtonFacing = (ImageView) view.findViewById(R.id.facing);
        if (CameraUtil.isChromium())
            mButtonFacing.setVisibility(View.INVISIBLE);
        mRecordDuration = (TextView) view.findViewById(R.id.recordDuration);
        mButtonFlash = (ImageView) view.findViewById(R.id.flash);
        setImageRes(mButtonFlash, mInterface.iconFlashOff());

        setupRecorderMode();
        setOnclick();

        if (savedInstanceState != null) {
            mOutputUri = savedInstanceState.getString("output_uri");
            boolean flashOn = savedInstanceState.getBoolean("flashOn");
            if (savedInstanceState.getBoolean("useVideo")) {
                setPreviewVideo();
            } else {
                setPreviewImg();
            }
            if (flashOn) {
                setImageRes(mButtonFlash, mInterface.iconFlashOn());
            } else {
                setImageRes(mButtonFlash, mInterface.iconFlashOff());
            }

        } else if (getArguments() != null) {
            if (getArguments().getBoolean("useVideo")) {
                setPreviewVideo();
            } else {
                setPreviewImg();
            }
            if (getArguments().getBoolean("flashOn")) {
                setImageRes(mButtonFlash, mInterface.iconFlashOn());
            } else {
                setImageRes(mButtonFlash, mInterface.iconFlashOff());
            }
        } else {
            setPreviewImg();
        }


        if (mInterface.autoRecordDelay() < 1000) {
            mDelayStartCountdown.setVisibility(View.GONE);
        } else {
            mDelayStartCountdown.setText(Long.toString(mInterface.autoRecordDelay() / 1000));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void setOnclick() {
        mButtonVideo.setOnClickListener(this);
        mButtonStillshot.setOnClickListener(this);
        mButtonFacing.setOnClickListener(this);
        mButtonFlash.setOnClickListener(this);
        mViewPreview.setOnClickListener(this);
    }

    private void setupRecorderMode() {
        if (mMediaRecorder != null && mIsRecording) {
            setImageRes(mButtonVideo, mInterface.iconStop());
        } else {
            setImageRes(mButtonVideo, mInterface.iconRecord());
            mInterface.setDidRecord(false);
        }
    }

    public void setPreviewVideo() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String previewVideoPath = preferences.getString(PREVIEW_VIDEO_KEY, "");
        if (TextUtils.isEmpty(previewVideoPath)) {
            setVideoSrc(getNewestVideoPath());
        } else {//得到最新照片
            setVideoSrc(previewVideoPath);
        }
    }

    /**
     * 设置预览图
     */
    public void setPreviewImg() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String previewImgName = preferences.getString(PREVIEW_IMG_KEY, "");
        if (TextUtils.isEmpty(previewImgName)) {
            //无预览,最近照片
            setImgSrc(getNewestPhotoPath());
        } else {//得到最新照片
            setImgSrc(previewImgName);
        }
    }

    /**
     * 获取最新的视频
     */
    private String getNewestVideoPath() {

        File pictureFile = getOutputPictureFile();
        File directory = pictureFile.getParentFile();


        if (directory.isDirectory()) {
            File[] files = directory.listFiles(new VideoFilenameFilter());
            if (files.length == 0) {
                return null;
            }
            long max = Long.parseLong(files[0].getName().split("\\.")[0]);
            String path = files[0].getAbsolutePath();
            for (int i = 1; i < files.length; i++) {
                long time = Long.parseLong(files[i].getName().split("\\.")[0]);
                if (time > max) {
                    max = time;
                    path = files[i].getAbsolutePath();
                }
            }
            return path;
        }
        return null;
    }

    /**
     * 获取最新的照片
     */
    @WorkerThread
    private String getNewestPhotoPath() {

        File pictureFile = getOutputPictureFile();
        File directory = pictureFile.getParentFile();

        if (directory.isDirectory()) {
            File[] files = directory.listFiles(new JPGFilenameFilter());
            if (files == null || files.length == 0) {
                return null;
            }
            long max = Long.parseLong(files[0].getName().split("\\.")[0]);
            String path = files[0].getAbsolutePath();
            for (int i = 1; i < files.length; i++) {
                long time = Long.parseLong(files[i].getName().split("\\.")[0]);
                if (time > max) {
                    max = time;
                    path = files[i].getAbsolutePath();
                }
            }
            return path;
        }
        return null;
    }

    private void setVideoSrc(String path) {
        if (path == null) {
            mViewPreview.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            mViewPreview.setImageResource(R.drawable.photo);
        } else if (!path.equalsIgnoreCase(mImgSrcPath)) {
            loadImg(path);
        }
    }

    /**
     * 加载图片
     */
    private void loadImg(String path) {
        Uri uri = null;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (!new File(path).exists()) {
            uri = GlideUtil.resourceIdToUri(getActivity(), R.drawable.photo);
            if (path.contains(".jpg")) {
                preferences.edit().remove(PREVIEW_IMG_KEY).apply();
            } else if (path.contains(".mp4")) {
                preferences.edit().remove(PREVIEW_VIDEO_KEY).apply();
            }
        } else if (path.contains(".jpg")) {
            mImgSrcPath = path;
            preferences.edit().putString(PREVIEW_IMG_KEY, path).apply();
        } else if (path.contains(".mp4")) {
            mImgSrcPath = path;
            preferences.edit().putString(PREVIEW_VIDEO_KEY, path).apply();
        }
        Glide.with(this).load(uri == null ? path : uri).asBitmap().centerCrop().into(mViewPreview);
    }


    private void setImgSrc(String path) {
        if (path == null) {
            mViewPreview.setImageResource(R.drawable.photo);
        } else if (!path.equalsIgnoreCase(mImgSrcPath)) {
            loadImg(path);
        }
    }


    private boolean mDidAutoRecord = false;
    private Handler mDelayHandler;
    private int mDelayCurrentSecond = -1;

    protected void onCameraOpened() {
        if (mDidAutoRecord || mInterface == null || mInterface.useStillshot() || mInterface.autoRecordDelay() < 0 || getActivity() == null) {
            mDelayStartCountdown.setVisibility(View.GONE);
            mDelayHandler = null;
            return;
        }
        mDidAutoRecord = true;
        //mButtonFacing.setVisibility(View.INVISIBLE);

        if (mInterface.autoRecordDelay() == 0) {
            mDelayStartCountdown.setVisibility(View.GONE);
            mIsRecording = startRecordingVideo();
            mDelayHandler = null;
            return;
        }

        mDelayHandler = new Handler();
        mButtonVideo.setEnabled(false);

        if (mInterface.autoRecordDelay() < 1000) {
            // Less than a second delay
            mDelayStartCountdown.setVisibility(View.GONE);
            mDelayHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isAdded() || getActivity() == null || mIsRecording) return;
                    mButtonVideo.setEnabled(true);
                    mIsRecording = startRecordingVideo();
                    mDelayHandler = null;
                }
            }, mInterface.autoRecordDelay());
            return;
        }

        mDelayStartCountdown.setVisibility(View.VISIBLE);
        mDelayCurrentSecond = (int) mInterface.autoRecordDelay() / 1000;
        mDelayHandler.postDelayed(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                if (!isAdded() || getActivity() == null || mIsRecording) return;
                mDelayCurrentSecond -= 1;
                mDelayStartCountdown.setText(Integer.toString(mDelayCurrentSecond));

                if (mDelayCurrentSecond == 0) {
                    mDelayStartCountdown.setVisibility(View.GONE);
                    mButtonVideo.setEnabled(true);
                    mIsRecording = startRecordingVideo();
                    mDelayHandler = null;
                    return;
                }

                mDelayHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mButtonVideo = null;
        mButtonStillshot = null;
        mButtonFacing = null;
        mButtonFlash = null;
        mRecordDuration = null;
        mViewPreview = null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public final void onAttach(Activity activity) {
        super.onAttach(activity);
        mInterface = (BaseCaptureInterface) activity;
    }

    @NonNull
    protected final File getOutputMediaFile() {
        return CameraUtil.makePictureFile(getActivity(), ".mp4");
    }

    @NonNull
    protected final File getOutputPictureFile() {
        return CameraUtil.makePictureFile(getActivity(), ".jpg");
    }

    public abstract void openCamera();

    public abstract void closeCamera();

    public void cleanup() {
        closeCamera();
        releaseRecorder();
        stopCounter();
    }

    public abstract void takeStillshot();

    public abstract void onPreferencesUpdated();

    @Override
    public void onPause() {
        super.onPause();
        cleanup();
    }

    @Override
    public final void onDetach() {
        super.onDetach();
        mInterface = null;
    }

    public final void startCounter() {
        if (mPositionHandler == null)
            mPositionHandler = new Handler();
        else mPositionHandler.removeCallbacks(mPositionUpdater);
        mPositionHandler.post(mPositionUpdater);
    }

    @BaseCaptureActivity.CameraPosition
    public final int getCurrentCameraPosition() {
        if (mInterface == null) return BaseCaptureActivity.CAMERA_POSITION_UNKNOWN;
        return mInterface.getCurrentCameraPosition();
    }

    public final int getCurrentCameraId() {
        if (mInterface.getCurrentCameraPosition() == BaseCaptureActivity.CAMERA_POSITION_BACK)
            return (Integer) mInterface.getBackCamera();
        else return (Integer) mInterface.getFrontCamera();
    }

    public final void stopCounter() {
        if (mPositionHandler != null) {
            mPositionHandler.removeCallbacks(mPositionUpdater);
            mPositionHandler = null;
        }
    }

    public final void releaseRecorder() {
        if (mMediaRecorder != null) {
            if (mIsRecording) {
                try {
                    mMediaRecorder.stop();
                } catch (Throwable t) {
                    //noinspection ResultOfMethodCallIgnored
                    new File(mOutputUri).delete();
                    t.printStackTrace();
                }
                mIsRecording = false;
            }
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    public boolean startRecordingVideo() {

        mRecordDuration.setVisibility(View.VISIBLE);
        final int orientation = Degrees.getActivityOrientation(getActivity());
        getActivity().setRequestedOrientation(orientation);
        mInterface.setDidRecord(true);
        return true;
    }

    public void stopRecordingVideo(boolean reachedZero) {
        mRecordDuration.setVisibility(View.GONE);
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    @Override
    public final void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("output_uri", mOutputUri);
        outState.putBoolean("useVideo", mInterface.fragmentFromVideo());
        outState.putBoolean("flashOn", mInterface.getFlashMode() == BaseCaptureActivity.FLASH_MODE_ALWAYS_ON);
    }

    @Override
    public final String getOutputUri() {
        return mOutputUri;
    }

    protected final void throwError(Exception e) {
        Activity act = getActivity();
        if (act != null) {
            act.setResult(RESULT_CANCELED, new Intent().putExtra(MaterialCamera.ERROR_EXTRA, e));
            act.finish();
        }
    }

    /**
     * 切换摄像头
     */
    public void exchangeCamera() {
        mInterface.toggleCameraPosition();
        closeCamera();
        openCamera();
    }

    @Override
    public void onClick(View view) {
        final int id = view.getId();
        if (id == R.id.facing) {
            exchangeCamera();
        } else if (id == R.id.video) {
            if (mIsRecording) {
                stopRecordingVideo(false);
                mIsRecording = false;
                mInterface.fromVideo(mImgSrcPath == null);
            } else {
                mIsRecording = startRecordingVideo();
            }
        } else if (id == R.id.stillshot) {
            takeStillshot();
            mInterface.fromVideo(mImgSrcPath == null);
        } else if (id == R.id.flash) {
            invalidateFlash();
        } else if (id == R.id.imagePreview) {
            previewLargeImgOrVideo();
        }
    }

    /**
     * 预览大图
     */
    private void previewLargeImgOrVideo() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (mImgSrcPath != null && !mImgSrcPath.contains("android") && mImgSrcPath.contains("jpg")) {
            intent.setDataAndType(Uri.fromFile(new File(mImgSrcPath)), "image/*");
            startActivity(intent);

        } else if (mImgSrcPath != null && !mImgSrcPath.contains("android") && mImgSrcPath.contains("mp4")) {

            Intent in = new Intent(getActivity(), PlayVideoActivity.class);
            in.putExtra("url", mImgSrcPath);
            startActivity(in);
            //intent.setDataAndType(Uri.fromFile(new File(mVideoSrcPath)), "video/mp4");
            //startActivity(intent);
        }
    }

    private void invalidateFlash() {
        //mInterface.toggleFlashMode();
        if (mInterface.getCurrentCameraPosition() == BaseCaptureActivity.CAMERA_POSITION_BACK) {
            if (mInterface.getFlashMode() == BaseCaptureActivity.FLASH_MODE_OFF) {
                mInterface.setFlashModes(BaseCaptureActivity.FLASH_MODE_ALWAYS_ON);
                setImageRes(mButtonFlash, mInterface.iconFlashOn());
            } else {
                mInterface.setFlashModes(BaseCaptureActivity.FLASH_MODE_OFF);
                setImageRes(mButtonFlash, mInterface.iconFlashOff());
            }
        }
        //setupFlashMode();
        onPreferencesUpdated();
    }


    public boolean isRecording() {
        return mIsRecording;
    }

    public BaseCameraFragment setRecording(boolean recording) {
        mIsRecording = recording;
        return this;
    }
}