package com.flarejaven.example.jnithread.util;

import android.graphics.Bitmap;

/**
 * Created by flarejaven on 2017/3/12
 */

public class ColorUtils {

    public static Bitmap createRGBBitmap(byte[] data, int width, int height, boolean hasAlpha) {
        int []colors = convertRGBByteToColor(data, hasAlpha);
        if (colors == null) {
            return null;
        }

        Bitmap bmp = null;
        try {
            bmp = Bitmap.createBitmap(colors, 0, width, width, height, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return bmp;
    }

    public static int[] convertRGBByteToColor(byte[] data, boolean hasAlpha) {
        int size = data.length;
        if (size == 0) {
            return null;
        }

        int[] color = hasAlpha ? new int[size / 4] : new int[size / 3];

        for(int i = 0; i < color.length; ++i) {
            if (hasAlpha) {
                color[i] = (data[i * 4] << 16 & 0x00FF0000) |    // R
                        (data[i * 4 + 1] << 8 & 0x0000FF00) | // G
                        (data[i * 4 + 2] & 0x000000FF) |      // B
                        (data[i * 4 + 3] << 24) & 0xFF000000; // A
            } else {
                color[i] = (data[i * 3] << 16 & 0x00FF0000) |
                        (data[i * 3 + 1] << 8 & 0x0000FF00) |
                        (data[i * 3 + 2] & 0x000000FF) |
                        0xFF000000;
            }
        }

        return color;
    }
}
