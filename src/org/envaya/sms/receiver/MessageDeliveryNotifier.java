package org.envaya.sms.receiver;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import org.envaya.sms.App;

public class MessageDeliveryNotifier extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        App app = (App) context.getApplicationContext();
        Uri uri = intent.getData();
        
        Bundle extras = intent.getExtras();
        int index = extras.getInt(App.STATUS_EXTRA_INDEX);
        int numParts = extras.getInt(App.STATUS_EXTRA_NUM_PARTS);
        
        app.log("Message " + uri + " part "+index + "/" + numParts + " delivered");
        
        // todo... could notify the server of message delivery
    }
}
