package com.afollestad.materialcamerasample;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.widget.Toast;

import com.umeng.analytics.MobclickAgent;
import com.wilddog.client.SyncReference;
import com.wilddog.client.WilddogSync;
import com.wilddog.wilddogcore.WilddogApp;
import com.wilddog.wilddogcore.WilddogOptions;

import java.util.Set;

/**
 * Created by neu on 2016/10/20.
 */

public class MuApplication extends Application {

    public static final String BLUETOOTH_NAME = "Shutter Camera";
    private static MuApplication sInstance = null;
    private boolean bluetoothConnected;
    private SyncReference ref;
    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        getConnectedBluetooth();
        MobclickAgent.openActivityDurationTrack(false);
        initWildDog();
    }

    private void initWildDog() {
        WilddogOptions options = new WilddogOptions.Builder().setSyncUrl("https://maikexiu.wilddogio.com").build();
        WilddogApp.initializeApp(this, options);
        ref = WilddogSync.getInstance().getReference();
    }
    public SyncReference getWilddogRef(){
        return ref;
    }


    private void getConnectedBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast.makeText(this,"您的设备不支持蓝牙",Toast.LENGTH_LONG).show();
        }else {
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if (pairedDevices != null && pairedDevices.size() > 0) {
                // Loop through paired devices
                for (BluetoothDevice device : pairedDevices) {

                    Log.e("device",device.getName());

                    if (BLUETOOTH_NAME.equals(device.getName())){
                        /*for (ParcelUuid uuid :device.getUuids()){
                            Log.e("BLUETOOTH_NAME",uuid.toString());
                        }*/
                        setBluetoothConnected(true);

                    }

                }
            }
        }

    }

    public static MuApplication getInstance() {
        return sInstance;
    }

    public boolean isBluetoothConnected() {
        return bluetoothConnected;
    }

    public MuApplication setBluetoothConnected(boolean bluetoothConnected) {
        this.bluetoothConnected = bluetoothConnected;
        return this;
    }

}
