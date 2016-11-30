package com.afollestad.materialcamerasample.camera.internal;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

import com.afollestad.easyvideoplayer.EasyVideoCallback;
import com.afollestad.easyvideoplayer.EasyVideoPlayer;
import com.afollestad.materialcamerasample.R;
import com.afollestad.materialcamerasample.camera.util.VideoFilenameFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by neu on 2016/11/5.
 */

public class PlayVideoActivity extends AppCompatActivity implements EasyVideoCallback {

    private EasyVideoPlayer mPlayer;
    private String mUrl;
    private List<String> mRankedUrlList;
    private int mCurrentPosition;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mUrl = getIntent().getStringExtra("url");
        setContentView(R.layout.activity_play_video);
        mPlayer = (EasyVideoPlayer) findViewById(R.id.playbackView);

        mPlayer.setSubmitTextRes(R.string.mcam_next_video);
        mPlayer.setRetryTextRes(R.string.mcam_before_video);
        mPlayer.setLeftAction(EasyVideoPlayer.LEFT_ACTION_RETRY);
        mPlayer.setRightAction(EasyVideoPlayer.RIGHT_ACTION_SUBMIT);

        mPlayer.setThemeColor(0);
        mPlayer.setCallback(this);
        mPlayer.setSource(Uri.parse(mUrl));


        mRankedUrlList = rankSource();
    }

    private List<String> rankSource() {
        List<String> list = new ArrayList<>();

        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        directory = new File(directory.getAbsolutePath() + File.separator + "Camera");
        File[] files = directory.listFiles(new VideoFilenameFilter());
        if (files == null || files.length == 0) {
            return null;
        }

        for (File file : files) {
            list.add(file.getAbsolutePath());
        }
        Collections.sort(list, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return (o1.split("\\.")[0]).compareTo(o2.split("\\.")[0]);
            }
        });
        mCurrentPosition = list.size() - 1;
        return list;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPlayer.pause();
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

    }

    @Override
    public void onCompletion(EasyVideoPlayer player) {

    }

    @Override
    public void onRetry(EasyVideoPlayer player, Uri source) {
        if (mRankedUrlList.size() == 1 || mCurrentPosition == 0) {
            mCurrentPosition = 0;
            mPlayer.reset();
            mPlayer.setSource(Uri.parse(mRankedUrlList.get(mCurrentPosition)));
        } else {
            mCurrentPosition = mCurrentPosition - 1;
            mPlayer.reset();
            mPlayer.setSource(Uri.parse(mRankedUrlList.get(mCurrentPosition)));
        }

    }

    @Override
    public void onSubmit(EasyVideoPlayer player, Uri source) {
        if (mRankedUrlList.size() == 1 || mCurrentPosition == mRankedUrlList.size() - 1) {
            mCurrentPosition = 0;
            mPlayer.reset();
            mPlayer.setSource(Uri.parse(mRankedUrlList.get(mCurrentPosition)));
        } else {
            mCurrentPosition = mCurrentPosition + 1;
            mPlayer.reset();
            mPlayer.setSource(Uri.parse(mRankedUrlList.get(mCurrentPosition)));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

}
