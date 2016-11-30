package com.afollestad.materialcamerasample.camera.internal;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.afollestad.easyvideoplayer.EasyVideoCallback;
import com.afollestad.easyvideoplayer.EasyVideoPlayer;
import com.afollestad.materialcamerasample.R;
import com.afollestad.materialcamerasample.camera.CaptureActivity;
import com.afollestad.materialdialogs.MaterialDialog;
import com.umeng.analytics.MobclickAgent;

import static com.afollestad.materialcamerasample.R.id.container;

/**
 * @author Aidan Follestad (afollestad)
 */
public class PlaybackVideoFragment extends Fragment implements CameraUriInterface, EasyVideoCallback {

    private EasyVideoPlayer mPlayer;
    private String mOutputUri;
    private BaseCaptureInterface mInterface;


    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mInterface = (BaseCaptureInterface) activity;
    }


    public static PlaybackVideoFragment newInstance(String outputUri, boolean allowRetry, int primaryColor) {
        PlaybackVideoFragment fragment = new PlaybackVideoFragment();
        //fragment.setRetainInstance(true);
        Bundle args = new Bundle();
        args.putString("output_uri", outputUri);
        args.putBoolean(CameraIntentKey.ALLOW_RETRY, allowRetry);
        args.putInt(CameraIntentKey.PRIMARY_COLOR, primaryColor);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("PlaybackVideoFragment");
    }


    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd("PlaybackVideoFragment");

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer.reset();
            mPlayer = null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        View view = inflater.inflate(R.layout.mcam_fragment_videoplayback, container, false);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mPlayer = (EasyVideoPlayer) view.findViewById(R.id.playbackView);
        mPlayer.setCallback(this);

        mPlayer.setSubmitTextRes(R.string.mcam_use_video);
        mPlayer.setRetryTextRes(mInterface.labelRetry());
        mPlayer.setPlayDrawableRes(mInterface.iconPlay());
        mPlayer.setPauseDrawableRes(mInterface.iconPause());

        if (getArguments().getBoolean(CameraIntentKey.ALLOW_RETRY, true))
            mPlayer.setLeftAction(EasyVideoPlayer.LEFT_ACTION_RETRY);
        mPlayer.setRightAction(EasyVideoPlayer.RIGHT_ACTION_SUBMIT);

        mPlayer.setThemeColor(getArguments().getInt(CameraIntentKey.PRIMARY_COLOR));
        mOutputUri = getArguments().getString("output_uri");

        mPlayer.setSource(Uri.parse(mOutputUri));
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    public void useVideo() {
        mInterface.fromVideo(true);
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        if (mInterface != null)
            mInterface.useVideo(mOutputUri);

        getActivity().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse(mOutputUri)));
    }

    @Override
    public String getOutputUri() {
        return getArguments().getString("output_uri");
    }

    @Override
    public void onStarted(EasyVideoPlayer player) {
    }

    @Override
    public void onPaused(EasyVideoPlayer player) {
    }

    @Override
    public void onPreparing(EasyVideoPlayer player) {
    }

    @Override
    public void onPrepared(EasyVideoPlayer player) {
    }

    @Override
    public void onBuffering(int percent) {
    }

    @Override
    public void onError(EasyVideoPlayer player, Exception e) {
        new MaterialDialog.Builder(getActivity())
                .title(R.string.mcam_error)
                .content(e.getMessage())
                .positiveText(android.R.string.ok)
                .show();
    }

    @Override
    public void onCompletion(EasyVideoPlayer player) {
    }

    @Override
    public void onRetry(EasyVideoPlayer player, Uri source) {
        if (mInterface != null)
            mInterface.onRetry(mOutputUri);
        ((CaptureActivity)getActivity()).setFragment(replaceNewVideo());
    }

    public CameraFragment replaceNewVideo() {
        CameraFragment cameraFragment = CameraFragment.newInstance();

        Bundle bundle = new Bundle();
        if (mInterface.fragmentFromVideo()){
            bundle.putBoolean("useVideo",true);
        }
        if (mInterface.getFlashMode() == BaseCaptureActivity.FLASH_MODE_OFF){
            bundle.putBoolean("flashOn",false);
        }else {
            bundle.putBoolean("flashOn",true);

        }
        cameraFragment.setArguments(bundle);

        getFragmentManager().beginTransaction()
                .replace(container, cameraFragment)
                .commit();
        return cameraFragment;
    }

    @Override
    public void onSubmit(EasyVideoPlayer player, Uri source) {
        useVideo();
        ((CaptureActivity)getActivity()).setFragment(replaceNewVideo());
    }
}