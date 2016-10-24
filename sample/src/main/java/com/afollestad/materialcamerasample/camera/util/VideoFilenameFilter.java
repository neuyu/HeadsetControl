package com.afollestad.materialcamerasample.camera.util;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Created by neu on 2016/10/20.
 */

public class VideoFilenameFilter implements FilenameFilter {

    public boolean accept(File dir, String name) {

        return name.endsWith("mp4") && !name.contains("VID") && !name.contains("_");
    }
}

