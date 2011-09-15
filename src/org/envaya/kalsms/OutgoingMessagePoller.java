package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OutgoingMessagePoller extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        App app = (App) context.getApplicationContext();
        app.checkOutgoingMessages();        
    }
}
