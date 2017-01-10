package org.envaya.sms.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.envaya.sms.App;

public class DequeueOutgoingMessageReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        App app = (App) context.getApplicationContext();
        
        if (!app.isEnabled())
        {
            return;
        }
        
        app.outbox.maybeDequeueMessage();
    }
}
