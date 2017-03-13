package com.flarejaven.example.jnithread;

import android.util.Log;

/**
 * Created by root on 17-3-10.
 */

public class NdkJniUtils {

    private static final String TAG = "NdkJniUtils";

    static {
        System.loadLibrary("jni-thread");
    }

    private OnDataComeListener listener;

    public void setOnMsgComeListener(OnDataComeListener listener) {
        this.listener = listener;
    }

    public native String startThread();
    public native String endThread();
    public native void setJNIEnv();

    public void callback(final byte[] data, int width, int height, int index) {
//        Log.d(TAG, "callback: " + data.length + " width = " + width + " height = " + height);
        if (listener != null) {
            listener.onDataCome(data, width, height, index);
        }
    }

    public interface OnDataComeListener {
        void onDataCome(byte[] data, int width, int height, int index);
    }
}
