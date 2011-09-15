package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MMSReceiver extends BroadcastReceiver {

    private App app;   

    @Override
    public void onReceive(Context context, Intent intent) {        
        app = (App) context.getApplicationContext();
        
        if (!app.isEnabled())
        {
            return;
        }
        
        app.log("WAP Push received");       
    }
}