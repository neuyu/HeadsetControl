package com.afollestad.materialcamerasample.camera.util;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Created by neu on 2016/10/11.
 */

public class JPGFilenameFilter implements FilenameFilter {

    public boolean accept(File dir, String name) {

        return name.endsWith("jpg") && !name.contains("IMG") && !name.contains("_");

    }
}
