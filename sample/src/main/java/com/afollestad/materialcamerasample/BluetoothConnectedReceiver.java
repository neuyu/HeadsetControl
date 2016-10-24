package com.afollestad.materialcamerasample;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by neu on 2016/10/20.
 */

public class BluetoothConnectedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device != null && MuApplication.BLUETOOTH_NAME.equals(device.getName())) {
            // TODO: 2016/10/20 不能以名称区别该蓝牙设备
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                MuApplication.getInstance().setBluetoothConnected(true);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                MuApplication.getInstance().setBluetoothConnected(false);

            }
        }
    }
}
