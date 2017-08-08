package com.paodong.smartpillow.uart;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.paodong.smartpillow.R;

/**
 * Created by hengfeng on 2017/8/6.
 */

public class UARTMainFragment extends Fragment {
    private final static String TAG = "UARTControlFragment";

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_feature_uart_main, container, false);

        return view;
    }


}
