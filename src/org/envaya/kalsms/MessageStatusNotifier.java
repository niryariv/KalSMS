/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

public class MessageStatusNotifier extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        App app = App.getInstance(context.getApplicationContext());
        
        String id = intent.getExtras().getString("id");        

        int resultCode = getResultCode();
        
        app.notifyOutgoingMessageStatus(id, resultCode);        
    }
}
