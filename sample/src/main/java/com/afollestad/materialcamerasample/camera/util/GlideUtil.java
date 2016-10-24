package com.afollestad.materialcamerasample.camera.util;

import android.content.Context;
import android.net.Uri;

/**
 * Created by neu on 2016/10/24.
 */

public class GlideUtil {
    public static final String ANDROID_RESOURCE = "android.resource://";
    public static final String FOREWARD_SLASH = "/";

    public static Uri resourceIdToUri(Context context, int resourceId) {
        return Uri.parse(ANDROID_RESOURCE + context.getPackageName() + FOREWARD_SLASH + resourceId);
    }
}
