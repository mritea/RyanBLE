package com.paodong.smartpillow.activity;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.paodong.smartpillow.R;
import com.paodong.smartpillow.profile.BleProfileService;
import com.paodong.smartpillow.scanner.DeviceListAdapter;
import com.paodong.smartpillow.scanner.ExtendedBluetoothDevice;
import com.paodong.smartpillow.scanner.RoundProgressBar;
import com.paodong.smartpillow.uart.UARTInterface;
import com.paodong.smartpillow.uart.UARTLocalLogContentProvider;
import com.paodong.smartpillow.uart.UARTService;
import com.paodong.smartpillow.utility.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LocalLogSession;
import no.nordicsemi.android.log.Logger;
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

/**
 * Created by hengfeng on 2017/8/8.
 */

public class ScannerActivity extends BaseActivity implements UARTInterface {

    private final static String TAG = "ScannerActivity";

    private UARTService.UARTBinder mServiceBinder;

    private static final String SIS_DEVICE_NAME = "device_name";
    private static final String SIS_DEVICE = "device";
    private static final String LOG_URI = "log_uri";


    //private BleProfileService.LocalBinder mService;

    private ListView mDeviceListview;

    //private TextView mDeviceNameView;
    //private TextView mBatteryLevelView;
    //private Button mConnectButton;

    private ILogSession mLogSession;
    private BluetoothDevice mBluetoothDevice;
    private String mDeviceName;

    private DeviceListAdapter mDeviceListAdapter;
    private Button mScanButton;
    private boolean mIsScanning = false;
    private BluetoothAdapter mBluetoothAdapter;

    private ParcelUuid mUuid;
    private final static String PARAM_UUID = "param_uuid";
    private final static long SCAN_DURATION = 10000;
    private int mTimerTick = 0;

    private final Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TIME_REFRESH:
                    int progress = msg.arg1;
                    if(mRoundProgressBar != null)
                        mRoundProgressBar.setProgress(progress > 10 ? 10 : progress);
                    break;
            }
        }
    };

    private final static int REQUEST_PERMISSION_REQ_CODE = 34; // any 8-bit number

    private RoundProgressBar mRoundProgressBar = null;
    private final int MAX_PROGRESS = 10;

    private Timer mTimer;
    private ProgressTimeeTask mTimerTask;


    class ProgressTimeeTask extends TimerTask{
        @Override
        public void run(){
            if(mIsScanning){
                Message msg = mHandler.obtainMessage();
                msg.what = TIME_REFRESH;
                msg.arg1 = mTimerTick++;
                mHandler.sendMessage(msg);
            }
        }
    };



    private final int TIME_REFRESH = 0x101;

    @Override
    protected final void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Restore the old log session
        if (savedInstanceState != null) {
            final Uri logUri = savedInstanceState.getParcelable(LOG_URI);
            mLogSession = Logger.openSession(getApplicationContext(), logUri);
        }

        // The onCreateView class should... create the view
        onCreateView(savedInstanceState); // 在子类中加载view

        // In onInitialize method a final class may register local broadcast receivers that will listen for events from the service
        onInitialize(savedInstanceState);
    }


    protected void onCreateView(final Bundle savedInstanceState) {
        setContentView(R.layout.scanner_main);

        // 自定义一个toolbar
        final Toolbar toolbar = (Toolbar) findViewById(R.id.scanner_toolbar_actionbar);
        setSupportActionBar(toolbar);
        //toolbar.setTitle(R.string.scanner_title);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(R.string.scanner_title);

        mDeviceListview = (ListView) findViewById(android.R.id.list);

        mScanButton = (Button) findViewById(R.id.action_cancel);


        mRoundProgressBar = (RoundProgressBar) findViewById(R.id.roundProgressBar);
        mRoundProgressBar.setMax(MAX_PROGRESS);
        //mConnectButton = (Button) findViewById(R.id.action_connect);
        //mDeviceNameView = (TextView) findViewById(R.id.device_name);
        //mBatteryLevelView = (TextView) findViewById(R.id.battery);
    }


    protected void onInitialize(final Bundle savedInstanceState) {
        // 做一些初始化
        final Intent args = getIntent();
        mUuid = args.getParcelableExtra(PARAM_UUID);
        DebugLogger.d(TAG, "onInitialize mUnid = "+mUuid);

        // 蓝牙设备
        final BluetoothManager manager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        mDeviceListAdapter = new DeviceListAdapter(this);
        mDeviceListview.setEmptyView(mDeviceListview.findViewById(android.R.id.empty));
        mDeviceListview.setAdapter(mDeviceListAdapter);

        mDeviceListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                // 选择某一个设备，开始连接设备
                stopScan();
                // 获取点击的设备
                final ExtendedBluetoothDevice d = (ExtendedBluetoothDevice) mDeviceListAdapter.getItem(position);
                onDeviceSelected(d.device, d.name);
            }
        });

        //mPermissionRationale = dialogView.findViewById(R.id.permission_rationale); // this is not null only on API23+

        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.action_cancel) {
                    if (!mIsScanning) {
                        startScan();
                    }
                }
            }
        });

        addBondedDevices();
        if (savedInstanceState == null)
            startScan(); // 开始扫描


        // v4包中的本地广播管理
        LocalBroadcastManager.getInstance(this).registerReceiver(mCommonBroadcastReceiver, makeIntentFilter());
    }

    @Override
    protected void onResume() {
        super.onResume();

		/*
		 * If the service has not been started before, the following lines will not start it. However, if it's running, the Activity will bind to it and
		 * notified via mServiceConnection.
		 */
        final Intent service = new Intent(this, getServiceClass());
        bindService(service, mServiceConnection, 0); // we pass 0 as a flag so the service will not be created if not exists

		/*
		 * * - When user exited the UARTActivity while being connected, the log session is kept in the service. We may not get it before binding to it so in this
		 * case this event will not be logged (mLogSession is null until onServiceConnected(..) is called). It will, however, be logged after the orientation changes.
		 */
    }


    @Override
    protected void onPause() {
        super.onPause();

        try {
            // We don't want to perform some operations (e.g. disable Battery Level notifications) in the service if we are just rotating the screen.
            // However, when the activity will disappear, we may want to disable some device features to reduce the battery consumption.
            if (mServiceBinder != null)
                mServiceBinder.setActivityIsChangingConfiguration(isChangingConfigurations());
            // 断开服务
            unbindService(mServiceConnection);

            Logger.d(mLogSession, "Activity unbound from the service");
            onServiceUnbinded();
            mDeviceName = null;
            mBluetoothDevice = null;
            mLogSession = null;
        } catch (final IllegalArgumentException e) {
            // do nothing, we were not connected to the sensor
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCommonBroadcastReceiver);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SIS_DEVICE_NAME, mDeviceName);
        outState.putParcelable(SIS_DEVICE, mBluetoothDevice);
        if (mLogSession != null)
            outState.putParcelable(LOG_URI, mLogSession.getSessionUri());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mDeviceName = savedInstanceState.getString(SIS_DEVICE_NAME);
        mBluetoothDevice = savedInstanceState.getParcelable(SIS_DEVICE);
    }


    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleProfileService.BROADCAST_CONNECTION_STATE);
        intentFilter.addAction(BleProfileService.BROADCAST_SERVICES_DISCOVERED);
        intentFilter.addAction(BleProfileService.BROADCAST_DEVICE_READY);
        intentFilter.addAction(BleProfileService.BROADCAST_BOND_STATE);
        intentFilter.addAction(BleProfileService.BROADCAST_BATTERY_LEVEL);
        intentFilter.addAction(BleProfileService.BROADCAST_ERROR);
        return intentFilter;
    }


    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            final UARTService.UARTBinder bleService = (UARTService.UARTBinder) service;
            onServiceBinded(bleService);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            // Note: this method is called only when the service is killed by the system,
            // not when it stops itself or is stopped by the activity.
            // It will be called only when there is critically low memory, in practice never
            // when the activity is in foreground.
            onServiceUnbinded();
        }
    };

    // 获取binder(service)，在这里可以通过它来调用send
    protected void onServiceBinded(final UARTService.UARTBinder binder) {
        mServiceBinder = binder;

        mBluetoothDevice = binder.getBluetoothDevice();
        mLogSession = binder.getLogSession();
        Logger.d(mLogSession, "Activity bound to the service");


        // Update UI
        mDeviceName = binder.getDeviceName();
        //mDeviceNameView.setText(mDeviceName);
        //mConnectButton.setText(R.string.action_disconnect);

        // And notify user if device is connected
        if (binder.isConnected()) {
            onDeviceConnected(mBluetoothDevice);
        } else {
            // If the device is not connected it means that either it is still connecting,
            // or the link was lost and service is trying to connect to it (autoConnect=true).
            onDeviceConnecting(mBluetoothDevice);
        }

    }

    protected void onServiceUnbinded() {
        mServiceBinder = null;
        Logger.d(mLogSession, "Activity disconnected from the service");
        //mDeviceNameView.setText(getDefaultDeviceName());
        //mConnectButton.setText(R.string.action_connect);

        //mService = null;
        mDeviceName = null;
        mBluetoothDevice = null;
        mLogSession = null;
    }


    // 处理各种localbroadcast
    private final BroadcastReceiver mCommonBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            // Check if the broadcast applies the connected device
            if (!isBroadcastForThisDevice(intent)) {
                DebugLogger.e(TAG, "error: is not BroadcastForThisDevice");
                return;
            }

            final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BleProfileService.EXTRA_DEVICE);
            final String action = intent.getAction();
            switch (action) {
                case BleProfileService.BROADCAST_CONNECTION_STATE: {
                    final int state = intent.getIntExtra(BleProfileService.EXTRA_CONNECTION_STATE, BleProfileService.STATE_DISCONNECTED);

                    switch (state) {
                        case BleProfileService.STATE_CONNECTED: {
                            mDeviceName = intent.getStringExtra(BleProfileService.EXTRA_DEVICE_NAME);
                            onDeviceConnected(bluetoothDevice);
                            break;
                        }
                        case BleProfileService.STATE_DISCONNECTED: {
                            onDeviceDisconnected(bluetoothDevice);
                            mDeviceName = null;
                            break;
                        }
                        case BleProfileService.STATE_LINK_LOSS: {
                            onLinklossOccur(bluetoothDevice);
                            break;
                        }
                        case BleProfileService.STATE_CONNECTING: {
                            onDeviceConnecting(bluetoothDevice);
                            break;
                        }
                        case BleProfileService.STATE_DISCONNECTING: {
                            onDeviceDisconnecting(bluetoothDevice);
                            break;
                        }
                        default:
                            // there should be no other actions
                            break;
                    }
                    break;
                }
                case BleProfileService.BROADCAST_SERVICES_DISCOVERED: {
                    final boolean primaryService = intent.getBooleanExtra(BleProfileService.EXTRA_SERVICE_PRIMARY, false);
                    final boolean secondaryService = intent.getBooleanExtra(BleProfileService.EXTRA_SERVICE_SECONDARY, false);

                    if (primaryService) {
                        onServicesDiscovered(bluetoothDevice, secondaryService);
                    } else {
                        onDeviceNotSupported(bluetoothDevice);
                    }
                    break;
                }
                case BleProfileService.BROADCAST_DEVICE_READY: {
                    onDeviceReady(bluetoothDevice);
                    break;
                }
                case BleProfileService.BROADCAST_BOND_STATE: {
                    final int state = intent.getIntExtra(BleProfileService.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                    switch (state) {
                        case BluetoothDevice.BOND_BONDING:
                            onBondingRequired(bluetoothDevice);
                            break;
                        case BluetoothDevice.BOND_BONDED:
                            onBonded(bluetoothDevice);
                            break;
                    }
                    break;
                }
                case BleProfileService.BROADCAST_BATTERY_LEVEL: {
                    final int value = intent.getIntExtra(BleProfileService.EXTRA_BATTERY_LEVEL, -1);
                    if (value > 0)
                        onBatteryValueReceived(bluetoothDevice, value);
                    break;
                }
                case BleProfileService.BROADCAST_ERROR: {
                    final String message = intent.getStringExtra(BleProfileService.EXTRA_ERROR_MESSAGE);
                    final int errorCode = intent.getIntExtra(BleProfileService.EXTRA_ERROR_CODE, 0);
                    onError(bluetoothDevice, message, errorCode);
                    break;
                }
            }
        }
    };


    // 发送数据
    @Override
    public void send(final String text) {
        if (mServiceBinder != null)
            mServiceBinder.send(text);
    }



    // 点击选择了某个设备的回调 ScannerFragment.OnDeviceSelectedListener
    public void onDeviceSelected(final BluetoothDevice device, final String name) {
        final int titleId = getLoggerProfileTitle();
        DebugLogger.d("zhfzhf", "onDeviceSelected titleId="+titleId);
        if (titleId > 0) {
            mLogSession = Logger.newSession(getApplicationContext(), getString(titleId), device.getAddress(), name);
            // If nRF Logger is not installed we may want to use local logger
            if (mLogSession == null && getLocalAuthorityLogger() != null) {
                mLogSession = LocalLogSession.newSession(getApplicationContext(), getLocalAuthorityLogger(), device.getAddress(), name);
            }
        }
        mBluetoothDevice = device;
        mDeviceName = name;
        //mDeviceNameView.setText(name != null ? name : getString(R.string.not_available));
        //mConnectButton.setText(R.string.action_connecting);

        // The device may not be in the range but the service will try to connect to it if it reach it
        Logger.d(mLogSession, "Creating service...");
        final Intent service = new Intent(this, getServiceClass());
        service.putExtra(BleProfileService.EXTRA_DEVICE_ADDRESS, device.getAddress());
        service.putExtra(BleProfileService.EXTRA_DEVICE_NAME, name);
        if (mLogSession != null)
            service.putExtra(BleProfileService.EXTRA_LOG_URI, mLogSession.getSessionUri());
        startService(service);
        Logger.d(mLogSession, "Binding to the service...");
        bindService(service, mServiceConnection, 0);
    }





    @Override
    public void onRequestPermissionsResult(final int requestCode, final @NonNull String[] permissions, final @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_REQ_CODE: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // We have been granted the Manifest.permission.ACCESS_COARSE_LOCATION permission. Now we may proceed with scanning.
                    startScan();
                } else {
                    //mPermissionRationale.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.no_required_permission, Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private void startScan() {
        // Since Android 6.0 we need to obtain either Manifest.permission.ACCESS_COARSE_LOCATION or Manifest.permission.ACCESS_FINE_LOCATION to be able to scan for
        // Bluetooth LE devices. This is related to beacons as proximity devices.
        // On API older than Marshmallow the following code does nothing.

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // When user pressed Deny and still wants to use this functionality, show the rationale
                /*
                * 如果用户选择了拒绝并且不再提醒，那么这个方法shouldShowRequestPermissionRationale会返回false，
                * 通过这一点，就可以在适当的时候展开一个对话框，告诉用户到底发生了什么，需要怎么做。

实际测试中发现，这个时候如果直接调用requestPermissions()也没用，因为刚才说了，已经选择不再提醒了。所以，需要告诉用户怎么打开权限：在app信息界面可以选择并控制所有的权限
                *
                * */
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION) /*&& mPermissionRationale.getVisibility() == View.GONE*/) {
                    //mPermissionRationale.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "自Android 6.0开始需要打开位置权限才可以搜索到Ble设备", Toast.LENGTH_SHORT).show();
                    return;
                }

                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_REQ_CODE);
                return;
            }
        }
        /*
        // Hide the rationale message, we don't need it anymore.
        if (mPermissionRationale != null)
            mPermissionRationale.setVisibility(View.GONE);
        */

        mDeviceListAdapter.clearDevices();
        mScanButton.setText(R.string.scanner_action_cancel);
        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        final ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(1000).setUseHardwareBatchingIfSupported(false).build();
        final List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setServiceUuid(mUuid).build());

        DebugLogger.d("zhfzhf", "ScannerFragment mUuid="+mUuid+", settings="+settings+",filters="+filters);
        scanner.startScan(filters, settings, scanCallback); // 这里很耗时！！！！！
        mIsScanning = true;

        mTimerTick = 0;
        mTimer = new Timer();
        mTimerTask = new ProgressTimeeTask();
        mRoundProgressBar.setProgress(0);
        mTimer.schedule(mTimerTask, 0 ,1000);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mIsScanning) {
                    stopScan();
                }
            }
        }, SCAN_DURATION);
    }

    private void stopScan() {
        if (mIsScanning) {
            //mRoundProgressBar.setProgress(MAX_PROGRESS);
            final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            scanner.stopScan(scanCallback);

            if(mTimerTask != null){
                mTimerTask.cancel();
                mTimerTask = null;
            }
            mTimer.purge();
            mTimer = null;

            mIsScanning = false;
            mScanButton.setText(R.string.scanner_action_scan);
        }
    }

    // 列出已经配对的设备
    private void addBondedDevices() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "mBluetoothAdapter==null", Toast.LENGTH_SHORT).show();
            return;
        }

        final Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        mDeviceListAdapter.addBondedDevices(devices);
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            // do nothing
        }

        @Override
        public void onBatchScanResults(final List<ScanResult> results) {
            mDeviceListAdapter.update(results);
        }

        @Override
        public void onScanFailed(final int errorCode) {
            // should never be called
        }
    };





    /**
     * Checks the {@link BleProfileService#EXTRA_DEVICE} in the given intent and compares it with the connected BluetoothDevice object.
     * @param intent intent received via a broadcast from the service
     * @return true if the data in the intent apply to the connected device, false otherwise
     */
    protected boolean isBroadcastForThisDevice(final Intent intent) {
        final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BleProfileService.EXTRA_DEVICE);
        return mBluetoothDevice != null && mBluetoothDevice.equals(bluetoothDevice);
    }

    public void onDeviceConnecting(final BluetoothDevice device) {
        // empty default implementation
        DebugLogger.d("zhfzhf", "onDeviceConnecting+++++");
    }

    public void onDeviceConnected(final BluetoothDevice device) {
        DebugLogger.d("zhfzhf", "onDeviceConnected++++");
        //mDeviceNameView.setText(mDeviceName);
        //mConnectButton.setText(R.string.action_disconnect);
    }

    public void onDeviceDisconnecting(final BluetoothDevice device) {
        DebugLogger.d("zhfzhf", "onDeviceDisconnecting---");
        // empty default implementation
    }

    public void onDeviceDisconnected(final BluetoothDevice device) {
        DebugLogger.d("zhfzhf", "onDeviceDisconnected----");
        //mConnectButton.setText(R.string.action_connect);
        //mDeviceNameView.setText(getDefaultDeviceName());
        //if (mBatteryLevelView != null)
        //    mBatteryLevelView.setText(R.string.not_available);

        try {
            Logger.d(mLogSession, "Unbinding from the service...");
            unbindService(mServiceConnection);
            mServiceBinder = null;

            Logger.d(mLogSession, "Activity unbound from the service");
            onServiceUnbinded();
            mDeviceName = null;
            mBluetoothDevice = null;
            mLogSession = null;
        } catch (final IllegalArgumentException e) {
            // do nothing. This should never happen but does...
        }
    }

    public void onLinklossOccur(final BluetoothDevice device) {
        //if (mBatteryLevelView != null)
        //    mBatteryLevelView.setText(R.string.not_available);
    }

    public void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound) {
        // empty default implementation
    }

    public void onDeviceReady(final BluetoothDevice device) {
        // empty default implementation
    }

    public void onBondingRequired(final BluetoothDevice device) {
        // empty default implementation
    }

    public void onBonded(final BluetoothDevice device) {
        // empty default implementation
    }

    public final boolean shouldEnableBatteryLevelNotifications(final BluetoothDevice device) {
        // This method will never be called.
        // Please see BleProfileService#shouldEnableBatteryLevelNotifications(BluetoothDevice) instead.
        throw new UnsupportedOperationException("This method should not be called");
    }

    public void onBatteryValueReceived(final BluetoothDevice device, final int value) {
        //if (mBatteryLevelView != null)
        //    mBatteryLevelView.setText(getString(R.string.battery, value));
    }

    public void onError(final BluetoothDevice device, final String message, final int errorCode) {
        DebugLogger.e(TAG, "Error occurred: " + message + ",  error code: " + errorCode);
        showToast(message + " (" + errorCode + ")");
    }

    public void onDeviceNotSupported(final BluetoothDevice device) {
        showToast(R.string.not_supported);
    }


    protected int getDefaultDeviceName() {
        return R.string.uart_default_name;
    }


    protected Uri getLocalAuthorityLogger() {
        return UARTLocalLogContentProvider.AUTHORITY_URI;
    }


    protected int getLoggerProfileTitle() {
        return R.string.uart_feature_title;
    }

    private Class<?> getServiceClass() {
        return UARTService.class;
    }


}