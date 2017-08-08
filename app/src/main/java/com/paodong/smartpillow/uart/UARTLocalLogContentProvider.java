package com.paodong.smartpillow.uart;

import android.net.Uri;

import no.nordicsemi.android.log.localprovider.LocalLogContentProvider;

/**
 * Created by hengfeng on 2017/8/7.
 */

// 这里ContentProvider的具体实现是在LocalLogContentProvider这个父类中
// 识别一个ContentProvider的关键就是uri
public class UARTLocalLogContentProvider extends LocalLogContentProvider {
    /** The authority for the contacts provider. */
    public final static String AUTHORITY = "com.paodong.smartpillow.uart.log";
    /** A content:// style uri to the authority for the log provider. */
    public final static Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    @Override
    protected Uri getAuthorityUri() {
        return AUTHORITY_URI;
    }
}
