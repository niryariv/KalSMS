package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OutgoingMessagePoller extends BroadcastReceiver {

    private App app;

    @Override
    public void onReceive(Context context, Intent intent) {
        app = App.getInstance(context.getApplicationContext());
        app.checkOutgoingMessages();
        app.retryStuckMessages(false);
    }
}
