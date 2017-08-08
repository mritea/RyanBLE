package com.paodong.smartpillow.utility;

import android.util.Log;

import com.paodong.smartpillow.global.Global;

/**
 * Created by hengfeng on 2017/8/6.
 */

public class DebugLogger {
    public static void v(final String tag, final String text) {
        if (Global.DEBUG)
            Log.v(tag, text);
    }

    public static void d(final String tag, final String text) {
        if (Global.DEBUG) {
            Log.d(tag, text);
        }
    }

    public static void i(final String tag, final String text) {
        if (Global.DEBUG)
            Log.i(tag, text);
    }

    public static void w(final String tag, final String text) {
        if (Global.DEBUG) {
            Log.w(tag, text);
        }
    }

    public static void e(final String tag, final String text) {
        if (Global.DEBUG)
            Log.e(tag, text);
    }

    public static void e(final String tag, final String text, final Throwable e) {
        if (Global.DEBUG)
            Log.e(tag, text, e);
    }

    public static void wtf(final String tag, final String text) {
        if (Global.DEBUG) {
            Log.wtf(tag, text);
        }
    }

    public static void wtf(final String tag, final String text, final Throwable e) {
        if (Global.DEBUG) {
            Log.wtf(tag, text, e);
        }
    }
}
