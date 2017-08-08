package com.paodong.smartpillow.scanner;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.paodong.smartpillow.R;
import com.paodong.smartpillow.utility.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

/**
 * Created by Ryan on 2017/8/3.
 */

public class ScannerFragment extends DialogFragment {

    private DeviceListAdapter mDeviceListAdapter;
    private Button mScanButton;
    private boolean mIsScanning = false;
    private BluetoothAdapter mBluetoothAdapter;

    private ParcelUuid mUuid;
    private final static String PARAM_UUID = "param_uuid";
    private final static long SCAN_DURATION = 5000;

    private final Handler mHandler = new Handler();

    private OnDeviceSelectedListener mListener;

    private final static int REQUEST_PERMISSION_REQ_CODE = 34; // any 8-bit number

    /**
     * Interface required to be implemented by activity.
     */
    public interface OnDeviceSelectedListener {
        /**
         * Fired when user selected the device. 用户选择来某个具体设备
         *
         * @param device
         *            the device to connect to
         * @param name
         *            the device name. Unfortunately on some devices {@link BluetoothDevice#getName()} always returns <code>null</code>, f.e. Sony Xperia Z1 (C6903) with Android 4.3. The name has to
         *            be parsed manually form the Advertisement packet.
         */
        void onDeviceSelected(final BluetoothDevice device, final String name);

        /**
         * 如果用户没有选择设备，而是直接取消来扫描对话框
         * Fired when scanner dialog has been cancelled without selecting a device.
         */
        void onDialogCanceled();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DebugLogger.d("zhfzhf", "ScannerFragment  onCreate");
        final Bundle args = getArguments();
        if (args.containsKey(PARAM_UUID)) {
            mUuid = args.getParcelable(PARAM_UUID);
        }

        // 蓝牙设备
        final BluetoothManager manager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        mDeviceListAdapter = new DeviceListAdapter(getActivity());
    }


    public static ScannerFragment getInstance(final UUID uuid) {
        final ScannerFragment fragment = new ScannerFragment();

        final Bundle args = new Bundle();
        if (uuid != null)
            args.putParcelable(PARAM_UUID, new ParcelUuid(uuid));
        fragment.setArguments(args);
        return fragment;
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.fragment_device_selection, null);
        final ListView listview = (ListView) dialogView.findViewById(android.R.id.list);

        listview.setEmptyView(dialogView.findViewById(android.R.id.empty));
        listview.setAdapter(mDeviceListAdapter);

        builder.setTitle(R.string.scanner_title);
        final AlertDialog dialog = builder.setView(dialogView).create();
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                // 选择某一个设备，开始连接设备
                stopScan();
                dialog.dismiss();
                // 获取点击的设备
                final ExtendedBluetoothDevice d = (ExtendedBluetoothDevice) mDeviceListAdapter.getItem(position);
                mListener.onDeviceSelected(d.device, d.name);
            }
        });

        //mPermissionRationale = dialogView.findViewById(R.id.permission_rationale); // this is not null only on API23+

        mScanButton = (Button) dialogView.findViewById(R.id.action_cancel);
        mScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.getId() == R.id.action_cancel) {
                    if (mIsScanning) {
                        dialog.cancel();
                    } else {
                        startScan();
                    }
                }
            }
        });

        addBondedDevices();
        if (savedInstanceState == null)
            startScan(); // 开始扫描
        return dialog;
    }


    /**
     * This will make sure that {@link OnDeviceSelectedListener} interface is implemented by activity.
     */
    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            this.mListener = (OnDeviceSelectedListener) context;
        } catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnDeviceSelectedListener");
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);

        mListener.onDialogCanceled();
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
                    Toast.makeText(getActivity(), R.string.no_required_permission, Toast.LENGTH_SHORT).show();
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
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // When user pressed Deny and still wants to use this functionality, show the rationale
                /*
                * 如果用户选择了拒绝并且不再提醒，那么这个方法shouldShowRequestPermissionRationale会返回false，
                * 通过这一点，就可以在适当的时候展开一个对话框，告诉用户到底发生了什么，需要怎么做。

实际测试中发现，这个时候如果直接调用requestPermissions()也没用，因为刚才说了，已经选择不再提醒了。所以，需要告诉用户怎么打开权限：在app信息界面可以选择并控制所有的权限
                *
                * */
                if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) /*&& mPermissionRationale.getVisibility() == View.GONE*/) {
                    //mPermissionRationale.setVisibility(View.VISIBLE);
                    Toast.makeText(getActivity(), "自Android 6.0开始需要打开位置权限才可以搜索到Ble设备", Toast.LENGTH_SHORT).show();
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
            mScanButton.setText(R.string.scanner_action_scan);

            final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            scanner.stopScan(scanCallback);

            mIsScanning = false;
        }
    }

    // 列出已经配对的设备
    private void addBondedDevices() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(getActivity(), "mBluetoothAdapter==null", Toast.LENGTH_SHORT).show();
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




}
