package com.afollestad.materialcamerasample.camera.internal;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v7.widget.AppCompatDrawableManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialcamerasample.R;
import com.afollestad.materialcamerasample.camera.MaterialCamera;
import com.afollestad.materialcamerasample.camera.util.CameraUtil;
import com.afollestad.materialcamerasample.camera.util.Degrees;
import com.afollestad.materialcamerasample.camera.util.GlideUtil;
import com.afollestad.materialcamerasample.camera.util.JPGFilenameFilter;
import com.afollestad.materialcamerasample.camera.util.VideoFilenameFilter;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;

import java.io.File;

import static android.app.Activity.RESULT_CANCELED;
import static com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity.CAMERA_POSITION_BACK;
import static com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity.FLASH_MODE_ALWAYS_ON;
import static com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity.FLASH_MODE_AUTO;
import static com.afollestad.materialcamerasample.camera.internal.BaseCaptureActivity.FLASH_MODE_OFF;

/**
 * @author Aidan Follestad (afollestad)
 */
abstract class BaseCameraFragment extends Fragment implements CameraUriInterface, View.OnClickListener {

    public static final String PREVIEW_IMG_KEY = "IMG";
    public static final String PREVIEW_VIDEO_KEY = "VIDEO";

    protected ImageButton mButtonVideo;
    protected ImageButton mButtonStillshot;
    protected ImageButton mButtonFacing;
    protected ImageButton mButtonFlash;
    protected TextView mRecordDuration;
    protected TextView mDelayStartCountdown;
    protected ImageView mViewPreview;

    private boolean mIsRecording;
    protected String mOutputUri;
    protected BaseCaptureInterface mInterface;
    protected Handler mPositionHandler;
    protected MediaRecorder mMediaRecorder;
    private int mIconTextColor;

    private String mImgSrcPath;
    private String mVideoSrcPath;


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
        return inflater.inflate(R.layout.mcam_fragment_videocapture, container, false);
    }

    protected void setImageRes(ImageView iv, @DrawableRes int res) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && iv.getBackground() instanceof RippleDrawable) {
            RippleDrawable rd = (RippleDrawable) iv.getBackground();
            rd.setColor(ColorStateList.valueOf(CameraUtil.adjustAlpha(mIconTextColor, 0.3f)));
        }
        Drawable d = AppCompatDrawableManager.get().getDrawable(iv.getContext(), res);
        //Drawable d = ContextCompat.getDrawable(iv.getContext(), res);
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, mIconTextColor);
        iv.setImageDrawable(d);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewPreview = (ImageView) view.findViewById(R.id.imagePreview);
        mDelayStartCountdown = (TextView) view.findViewById(R.id.delayStartCountdown);
        mButtonVideo = (ImageButton) view.findViewById(R.id.video);
        mButtonStillshot = (ImageButton) view.findViewById(R.id.stillshot);
        mButtonFacing = (ImageButton) view.findViewById(R.id.facing);
        if (CameraUtil.isChromium())
            mButtonFacing.setVisibility(View.GONE);
        mRecordDuration = (TextView) view.findViewById(R.id.recordDuration);
        setImageRes(mButtonFacing, mInterface.getCurrentCameraPosition() == CAMERA_POSITION_BACK ?
                mInterface.iconFrontCamera() : mInterface.iconRearCamera());

        mButtonFlash = (ImageButton) view.findViewById(R.id.flash);
        setupFlashMode();

        mButtonVideo.setOnClickListener(this);
        mButtonStillshot.setOnClickListener(this);
        mButtonFacing.setOnClickListener(this);
        mButtonFlash.setOnClickListener(this);
        mViewPreview.setOnClickListener(this);

        mIconTextColor = ContextCompat.getColor(getActivity(), R.color.mcam_color_light);
        mRecordDuration.setTextColor(mIconTextColor);

        if (mMediaRecorder != null && mIsRecording) {
            setImageRes(mButtonVideo, mInterface.iconStop());
        } else {
            setImageRes(mButtonVideo, mInterface.iconRecord());
            mInterface.setDidRecord(false);
        }

        if (savedInstanceState != null)
            mOutputUri = savedInstanceState.getString("output_uri");

        if (mInterface.useStillshot()) {
            mButtonVideo.setVisibility(View.GONE);
            mRecordDuration.setVisibility(View.GONE);
            mButtonStillshot.setVisibility(View.VISIBLE);
            mViewPreview.setVisibility(View.VISIBLE);
            setImageRes(mButtonStillshot, mInterface.iconStillshot());
            mButtonFlash.setVisibility(View.VISIBLE);
            setPreviewImg();
        } else {
            mRecordDuration.setVisibility(View.GONE);
            mViewPreview.setVisibility(View.VISIBLE);
            setPreviewVideo();
        }

        if (mInterface.autoRecordDelay() < 1000) {
            mDelayStartCountdown.setVisibility(View.GONE);
        } else {
            mDelayStartCountdown.setText(Long.toString(mInterface.autoRecordDelay() / 1000));
        }
    }

    private void setPreviewVideo() {
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
    private void setPreviewImg() {
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

    private void setVideoSrc(String path) {
        if (path == null) {
            Uri uri = GlideUtil.resourceIdToUri(getActivity(), R.mipmap.ic_launcher);
            mVideoSrcPath = uri.toString();
            Glide.with(this).load(uri).asBitmap().centerCrop().into(new BitmapImageViewTarget(mViewPreview) {
                @Override
                protected void setResource(Bitmap resource) {
                    RoundedBitmapDrawable circularBitmapDrawable =
                            RoundedBitmapDrawableFactory.create(getResources(), resource);
                    circularBitmapDrawable.setCircular(true);
                    mViewPreview.setImageDrawable(circularBitmapDrawable);
                }
            });
        } else if (!path.equalsIgnoreCase(mVideoSrcPath)) {
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
            uri = GlideUtil.resourceIdToUri(getActivity(), R.mipmap.ic_launcher);
            if (path.contains(".jpg")) {
                preferences.edit().remove(PREVIEW_IMG_KEY).apply();
            } else if (path.contains(".mp4")) {
                preferences.edit().remove(PREVIEW_VIDEO_KEY).apply();
            }
        } else if (path.contains(".jpg")) {
            mImgSrcPath = path;
            preferences.edit().putString(PREVIEW_IMG_KEY, path).apply();
        } else if (path.contains(".mp4")) {
            mVideoSrcPath = path;
            preferences.edit().putString(PREVIEW_VIDEO_KEY, path).apply();
        }

        Glide.with(this).load(uri == null ? path : uri).asBitmap().centerCrop().into(new BitmapImageViewTarget(mViewPreview) {
            @Override
            protected void setResource(Bitmap resource) {
                RoundedBitmapDrawable circularBitmapDrawable =
                        RoundedBitmapDrawableFactory.create(getResources(), resource);
                circularBitmapDrawable.setCircular(true);
                mViewPreview.setImageDrawable(circularBitmapDrawable);
            }
        });
    }


    private void setImgSrc(String path) {
        if (path == null) {
            Uri uri = GlideUtil.resourceIdToUri(getActivity(), R.mipmap.ic_launcher);
            mImgSrcPath = uri.toString();
            Glide.with(this).load(uri).asBitmap().centerCrop().into(new BitmapImageViewTarget(mViewPreview) {
                @Override
                protected void setResource(Bitmap resource) {
                    RoundedBitmapDrawable circularBitmapDrawable =
                            RoundedBitmapDrawableFactory.create(getResources(), resource);
                    circularBitmapDrawable.setCircular(true);
                    mViewPreview.setImageDrawable(circularBitmapDrawable);
                }
            });
        } else if (!path.equalsIgnoreCase(mImgSrcPath)) {
            loadImg(path);
        }
    }

    protected void onFlashModesLoaded() {
        if (getCurrentCameraPosition() != BaseCaptureActivity.CAMERA_POSITION_FRONT) {
            invalidateFlash();
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
        mButtonFacing.setVisibility(View.GONE);

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

    @Override
    public void onResume() {
        super.onResume();
        if (mInterface != null && mInterface.hasLengthLimit()) {
            if (mInterface.countdownImmediately() || mInterface.getRecordingStart() > -1) {
                if (mInterface.getRecordingStart() == -1)
                    mInterface.setRecordingStart(System.currentTimeMillis());
                startCounter();
            } else {
                mRecordDuration.setText(String.format("-%s", CameraUtil.getDurationString(mInterface.getLengthLimit())));
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public final void onAttach(Activity activity) {
        super.onAttach(activity);
        mInterface = (BaseCaptureInterface) activity;
    }

    @NonNull
    protected final File getOutputMediaFile() {
        return CameraUtil.makePictureFile(getActivity(), getArguments().getString(CameraIntentKey.SAVE_DIR), ".mp4");
    }

    @NonNull
    protected final File getOutputPictureFile() {
        return CameraUtil.makePictureFile(getActivity(), getArguments().getString(CameraIntentKey.SAVE_DIR), ".jpg");
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

        if (mInterface != null && mInterface.hasLengthLimit() && !mInterface.countdownImmediately()) {
            // Countdown wasn't started in onResume, start it now
            if (mInterface.getRecordingStart() == -1)
                mInterface.setRecordingStart(System.currentTimeMillis());
            startCounter();
        }

        final int orientation = Degrees.getActivityOrientation(getActivity());
        //noinspection ResourceType
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
        setImageRes(mButtonFacing, mInterface.getCurrentCameraPosition() == BaseCaptureActivity.CAMERA_POSITION_BACK ?
                mInterface.iconFrontCamera() : mInterface.iconRearCamera());
        closeCamera();
        openCamera();
        setupFlashMode();
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
            } else {
                if (getArguments().getBoolean(CameraIntentKey.SHOW_PORTRAIT_WARNING, true) &&
                        Degrees.isPortrait(getActivity())) {
                    new MaterialDialog.Builder(getActivity())
                            .title(R.string.mcam_portrait)
                            .content(R.string.mcam_portrait_warning)
                            .positiveText(R.string.mcam_yes)
                            .negativeText(android.R.string.cancel)
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                @Override
                                public void onClick(@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) {
                                    mIsRecording = startRecordingVideo();
                                }
                            })
                            .show();
                } else {
                    mIsRecording = startRecordingVideo();
                }
            }
        } else if (id == R.id.stillshot) {
            takeStillshot();
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
        if (mImgSrcPath != null && !mImgSrcPath.contains("android")) {
            intent.setDataAndType(Uri.fromFile(new File(mImgSrcPath)), "image/*");
            startActivity(intent);

        } else if (mVideoSrcPath != null && !mVideoSrcPath.contains("android")) {
            intent.setDataAndType(Uri.fromFile(new File(mVideoSrcPath)), "video/mp4");
            startActivity(intent);
        }
    }

    private void invalidateFlash() {
        mInterface.toggleFlashMode();
        setupFlashMode();
        onPreferencesUpdated();
    }

    private void setupFlashMode() {
        if (mInterface.shouldHideFlash()) {
            mButtonFlash.setVisibility(View.GONE);
            return;
        } else {
            mButtonFlash.setVisibility(View.VISIBLE);
        }

        final int res;
        switch (mInterface.getFlashMode()) {
            case FLASH_MODE_AUTO:
                res = mInterface.iconFlashAuto();
                break;
            case FLASH_MODE_ALWAYS_ON:
                res = mInterface.iconFlashOn();
                break;
            case FLASH_MODE_OFF:
            default:
                res = mInterface.iconFlashOff();
        }

        setImageRes(mButtonFlash, res);
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    public BaseCameraFragment setRecording(boolean recording) {
        mIsRecording = recording;
        return this;
    }
}