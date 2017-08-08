package com.paodong.smartpillow.activity;

import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.SlidingPaneLayout;
import android.view.View;

import com.paodong.smartpillow.R;
import com.paodong.smartpillow.profile.BleProfileService;
import com.paodong.smartpillow.profile.BleProfileServiceReadyActivity;
import com.paodong.smartpillow.uart.UARTInterface;
import com.paodong.smartpillow.uart.UARTLocalLogContentProvider;
import com.paodong.smartpillow.uart.UARTLogFragment;
import com.paodong.smartpillow.uart.UARTService;

import java.util.UUID;

public class MainActivity extends BleProfileServiceReadyActivity<UARTService.UARTBinder> implements UARTInterface {

    private final static String TAG = "UARTActivity";

//    private final static String PREFS_BUTTON_ENABLED = "prefs_uart_enabled_";
//    private final static String PREFS_BUTTON_COMMAND = "prefs_uart_command_";
//    private final static String PREFS_BUTTON_ICON = "prefs_uart_icon_";
//    /** This preference keeps the ID of the selected configuration. */
//    private final static String PREFS_CONFIGURATION = "configuration_id";
//    /** This preference is set to true when initial data synchronization for wearables has been completed. */
//    private final static String PREFS_WEAR_SYNCED = "prefs_uart_synced";
//    private final static String SIS_EDIT_MODE = "sis_edit_mode";
//
//    private final static int SELECT_FILE_REQ = 2678; // random
//    private final static int PERMISSION_REQ = 24; // random, 8-bit

    private SlidingPaneLayout mSlider;

    private UARTService.UARTBinder mServiceBinder;

    @Override
    protected Class<? extends BleProfileService> getServiceClass() {
        return UARTService.class;
    }

    @Override
    protected void setDefaultUI() {

    }

    @Override
    protected int getLoggerProfileTitle() {
        return R.string.uart_feature_title;
    }

    @Override
    protected Uri getLocalAuthorityLogger() {
        return UARTLocalLogContentProvider.AUTHORITY_URI;
    }

    // 获取binder(service)，在这里可以通过它来调用send
    @Override
    protected void onServiceBinded(final UARTService.UARTBinder binder) {
        mServiceBinder = binder;
    }

    @Override
    protected void onServiceUnbinded() {
        mServiceBinder = null;
    }

    @Override
    protected void onInitialize(final Bundle savedInstanceState) {
        // 做一些初始化
    }

    @Override
    protected void onCreateView(final Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);

        // Setup the sliding pane if it exists
        final SlidingPaneLayout slidingPane = mSlider = (SlidingPaneLayout) findViewById(R.id.sliding_pane);
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

    @Override
    protected void onViewCreated(final Bundle savedInstanceState) {
        // 不显示标题， 这个函数由父类在onCreate中调用
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }


    @Override
    public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {
        // do nothing
    }


    @Override
    public void onDeviceSelected(final BluetoothDevice device, final String name) {
        // The super method starts the service
        super.onDeviceSelected(device, name);

        // Notify the log fragment about it
        final UARTLogFragment logFragment = (UARTLogFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_log);
        logFragment.onServiceStarted();
    }

    @Override
    protected int getDefaultDeviceName() {
        return R.string.uart_default_name;
    }

    @Override
    protected int getAboutTextId() {
        return R.string.uart_about_text;
    }

    @Override
    protected UUID getFilterUUID() {
        return null; // not used
    }


    // 发送数据
    @Override
    public void send(final String text) {
        if (mServiceBinder != null)
            mServiceBinder.send(text);
    }





}