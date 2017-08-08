package com.paodong.smartpillow.uart;

import android.bluetooth.BluetoothDevice;

import com.paodong.smartpillow.profile.BleManagerCallbacks;

/**
 * Created by hengfeng on 2017/8/6.
 */

public interface UARTManagerCallbacks extends BleManagerCallbacks {

    void onDataReceived(final BluetoothDevice device, final String data);

    void onDataSent(final BluetoothDevice device, final String data);
}
