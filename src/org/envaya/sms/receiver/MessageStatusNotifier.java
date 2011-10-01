/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.envaya.sms.receiver;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import org.envaya.sms.App;
import org.envaya.sms.OutgoingMessage;

public class MessageStatusNotifier extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        App app = (App) context.getApplicationContext();
        Uri uri = intent.getData();
        
        Bundle extras = intent.getExtras();
        int index = extras.getInt(App.STATUS_EXTRA_INDEX);
        //int numParts = extras.getInt(App.STATUS_EXTRA_NUM_PARTS);           
        
        OutgoingMessage sms = app.outbox.getMessage(uri);

        if (sms == null) {
            return;
        }
        
        if (index != 0)
        {
            // TODO: process message status for parts other than the first one
            return;
        }
            
        int resultCode = getResultCode();
        
        /*
        // uncomment to test retry on outgoing message failure          
        if (Math.random() > 0.4)
        {
            resultCode = SmsManager.RESULT_ERROR_NO_SERVICE;
        }
        */
        
        if (resultCode == Activity.RESULT_OK)
        {
            app.outbox.messageSent(sms);
        }
        else
        {
            app.outbox.messageFailed(sms, getErrorMessage(resultCode));        
        }
    }
    
    public String getErrorMessage(int resultCode)
    {
        switch (resultCode) {
            case Activity.RESULT_OK:
                return "";
            case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                return "generic failure";
            case SmsManager.RESULT_ERROR_RADIO_OFF:
                return "radio off";
            case SmsManager.RESULT_ERROR_NO_SERVICE:
                return "no service";
            case SmsManager.RESULT_ERROR_NULL_PDU:
                return "null PDU";
            default:
                return "unknown error";                
        }        
                
    }
}
