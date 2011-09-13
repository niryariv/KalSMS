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
        
        String id = intent.getData().getLastPathSegment();

        int resultCode = getResultCode();
        
        // uncomment to test retry on outgoing message failure
        /*              
        if (Math.random() > 0.4)
        {
            resultCode = SmsManager.RESULT_ERROR_NO_SERVICE;
        }        
        */
        
        app.notifyOutgoingMessageStatus(id, resultCode);        
    }
}
