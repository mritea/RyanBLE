package com.paodong.smartpillow.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.widget.SlidingPaneLayout;
import android.view.View;

import com.paodong.smartpillow.R;
import com.paodong.smartpillow.uart.UARTLogFragment;
import com.paodong.smartpillow.uart.UARTService;

public class MainActivity extends BaseActivity {

    private final static String TAG = "MainActivity";

    private SlidingPaneLayout mSlider;


    private UARTService.UARTBinder mServiceBinder;

    @Override
    protected final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // Setup the sliding pane if it exists
        final SlidingPaneLayout slidingPane = mSlider = (SlidingPaneLayout) findViewById(R.id.main_sliding_pane);
        if (slidingPane != null) {
            slidingPane.setSliderFadeColor(Color.TRANSPARENT);
            //slidingPane.setShadowResourceLeft(R.drawable.shadow_r);
            slidingPane.setPanelSlideListener(new SlidingPaneLayout.SimplePanelSlideListener() {
                @Override
                public void onPanelClosed(final View panel) {
                    // Close the keyboar
                    // 收起来的时候，把log fragement隐藏起来
                    final UARTLogFragment logFragment = (UARTLogFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_log);
                    logFragment.onFragmentHidden();
                }
            });
        }
    }


    public void onConnectClicked(View v){
        Intent intent = new Intent(this, ScannerActivity.class);
        startActivity(intent);
    }


    @Override
    public void onBackPressed() {
        if (mSlider != null && mSlider.isOpen()) {
            mSlider.closePane();
            return;
        }
        super.onBackPressed();
    }


//    // 获取binder(service)，在这里可以通过它来调用send
//    @Override
//    protected void onServiceBinded(final UARTService.UARTBinder binder) {
//        mServiceBinder = binder;
//    }
//
//    @Override
//    protected void onServiceUnbinded() {
//        mServiceBinder = null;
//    }


    // 发送数据
//    @Override
//    public void send(final String text) {
//        if (mServiceBinder != null)
//            mServiceBinder.send(text);
//    }


}