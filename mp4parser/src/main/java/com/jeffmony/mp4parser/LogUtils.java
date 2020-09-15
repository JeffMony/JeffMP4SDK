package com.jeffmony.mp4parser;

import android.util.Log;

public class LogUtils {

    public static final String TAG = "JeffMP4SDK";

    public static final boolean DEBUG = true;
    public static final boolean INFO = true;
    public static final boolean WARN = true;
    public static final boolean ERROR = true;

    public static void d(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    public static void i(String msg) {
        if (INFO) {
            Log.i(TAG, msg);
        }
    }

    public static void w(String msg) {
        if (WARN) {
            Log.w(TAG, msg);
        }
    }

    public static void e(String msg) {
        if (ERROR) {
            Log.e(TAG, msg);
        }
    }
}
