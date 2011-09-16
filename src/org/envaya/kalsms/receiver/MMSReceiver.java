/*
 * Based on http://code.google.com/p/android-notifier/, copyright 2011 Rodrigo Damazio
 * Licensed under the Apache License, Version 2.0
 */

package org.envaya.kalsms.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.envaya.kalsms.App;

public class MMSReceiver extends BroadcastReceiver {

    private App app;

    @Override
    public void onReceive(Context context, Intent intent) {
        app = (App) context.getApplicationContext();

        if (!app.isEnabled()) {
            return;
        }
        
        app.log("WAP Push received");
    }
}