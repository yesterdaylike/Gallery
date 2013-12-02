package com.zheng.gallery3d.data;

import android.util.Log;

import com.zheng.gallery3d.util.MediaSetUtils;

public final class DataSourceType {
    public static final int TYPE_NOT_CATEGORIZED = 0;
    public static final int TYPE_LOCAL = 1;
    public static final int TYPE_PICASA = 2;
    public static final int TYPE_MTP = 3;
    public static final int TYPE_CAMERA = 4;

    private static final Path PICASA_ROOT = Path.fromString("/picasa");
    private static final Path LOCAL_ROOT = Path.fromString("/local");
    private static final Path MTP_ROOT = Path.fromString("/mtp");

    public static int identifySourceType(MediaSet set) {
        if (set == null) {
            return TYPE_NOT_CATEGORIZED;
        }

        Path path = set.getPath();
        if (MediaSetUtils.isCameraSource(path)) return TYPE_CAMERA;

        Path prefix = path.getPrefixPath();

        if (prefix == PICASA_ROOT) return TYPE_PICASA;
        if (prefix == MTP_ROOT) return TYPE_MTP;
        if (prefix == LOCAL_ROOT) return TYPE_LOCAL;

        Log.v("identifySourceType", set.getName());
        
        return TYPE_NOT_CATEGORIZED;
    }
}
