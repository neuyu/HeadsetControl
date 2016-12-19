package com.afollestad.materialcamerasample;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;

import com.wilddog.client.DataSnapshot;
import com.wilddog.client.SyncError;
import com.wilddog.client.SyncReference;
import com.wilddog.client.ValueEventListener;

import java.util.Iterator;

/**
 * Created by neu on 2016/12/13.
 */

public class Utils {

    public static void permittedPhone(final Context context, final Handler mHandler) {
        SyncReference reference = MuApplication.getInstance().getWilddogRef();
        // dataSnapshot 里面的数据会一直和云端保持同步
        reference.child("imei").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.getValue() != null) {
                    boolean match = matchIMEI(context, dataSnapshot);
                    Message message = Message.obtain();
                    message.what = 0;
                    message.obj = match;
                    mHandler.sendMessage(message);
                }
            }

            @Override
            public void onCancelled(SyncError syncError) {
            }
        });
    }

    public static boolean matchIMEI(Context context, DataSnapshot snapshot) {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String imei = tm.getDeviceId();
        boolean matchIMEI = false;
        Iterator iterator = snapshot.getChildren().iterator();
        while (iterator.hasNext()) {
            DataSnapshot user = (DataSnapshot) iterator.next();
            if (user.getValue().equals(imei)) {
                matchIMEI = true;
                break;
            }
        }
        return matchIMEI;
    }

}
