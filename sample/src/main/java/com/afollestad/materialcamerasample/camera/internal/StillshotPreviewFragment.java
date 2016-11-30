package com.afollestad.materialcamerasample.camera.internal;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import com.afollestad.materialcamerasample.R;
import com.afollestad.materialcamerasample.camera.CaptureActivity;
import com.afollestad.materialcamerasample.camera.util.ImageUtil;
import com.umeng.analytics.MobclickAgent;

public class StillshotPreviewFragment extends BaseGalleryFragment {

    private ImageView mImageView;

    /**
     * Reference to the bitmap, in case 'onConfigurationChange' event comes, so we do not recreate the bitmap
     */
    private static Bitmap mBitmap;

    public static StillshotPreviewFragment newInstance(String outputUri, boolean allowRetry, int primaryColor) {
        final StillshotPreviewFragment fragment = new StillshotPreviewFragment();
        //fragment.setRetainInstance(true);
        Bundle args = new Bundle();
        args.putString("output_uri", outputUri);
        args.putBoolean(CameraIntentKey.ALLOW_RETRY, allowRetry);
        args.putInt(CameraIntentKey.PRIMARY_COLOR, primaryColor);
        fragment.setArguments(args);
        return fragment;
    }

    public static StillshotPreviewFragment newInstance(Bitmap bitmap, String outputUri, boolean allowRetry, int primaryColor) {
        final StillshotPreviewFragment fragment = new StillshotPreviewFragment();
        //fragment.setRetainInstance(true);
        Bundle args = new Bundle();
        args.putParcelable("bitmap",bitmap);
        args.putString("output_uri", outputUri);
        args.putBoolean(CameraIntentKey.ALLOW_RETRY, allowRetry);
        args.putInt(CameraIntentKey.PRIMARY_COLOR, primaryColor);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        View view = inflater.inflate(R.layout.mcam_fragment_stillshot, container, false);
        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mImageView = (ImageView) view.findViewById(R.id.stillshot_imageview);

        mConfirm.setText("保存图片");
        mRetry.setText(mInterface.labelRetry());

        mRetry.setOnClickListener(this);
        mConfirm.setOnClickListener(this);

        mImageView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                setImageBitmap();
                mImageView.getViewTreeObserver().removeOnPreDrawListener(this);

                return true;
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mBitmap != null && !mBitmap.isRecycled()) {
            try {
                mBitmap.recycle();
                mBitmap = null;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }


    /**
     * Sets bitmap to ImageView widget
     */
    private void setImageBitmap() {
        final int width = mImageView.getMeasuredWidth();
        final int height = mImageView.getMeasuredHeight();

        // TODO IMPROVE MEMORY USAGE HERE, ESPECIALLY ON LOW-END DEVICES.
        Bitmap bitmap = getArguments().getParcelable("bitmap");
        if (bitmap != null) mBitmap = bitmap;
        if (mBitmap == null)
            mBitmap = ImageUtil.getRotatedBitmap(Uri.parse(mOutputUri).getPath(), width, height);

        if (mBitmap == null)
            showDialog(getString(R.string.mcam_image_preview_error_title), getString(R.string.mcam_image_preview_error_message));
        else
            mImageView.setImageBitmap(mBitmap);
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("StillshotPreviewFragment");
    }

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd("StillshotPreviewFragment");
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.retry)
            mInterface.onRetry(mOutputUri);
        else if (v.getId() == R.id.confirm) {
            useVideo();
        }

        ((CaptureActivity)getActivity()).setFragment(gotoCameraFragment());

    }

    public CameraFragment gotoCameraFragment() {
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
                .replace(R.id.container, cameraFragment)
                .commit();

        return cameraFragment;

    }

    public void useVideo() {
        mInterface.fromVideo(false);
        mInterface.useVideo(mOutputUri);
        getActivity().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + mOutputUri)));
    }
}