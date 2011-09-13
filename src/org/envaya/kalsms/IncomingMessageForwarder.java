package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;


public class IncomingMessageForwarder extends BroadcastReceiver {

    private App app;   

    @Override
    // source: http://www.devx.com/wireless/Article/39495/1954
    public void onReceive(Context context, Intent intent) {        
        app = App.getInstance(context.getApplicationContext());
        
        try {
            String action = intent.getAction();

            if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {
                
                for (SmsMessage sms : getMessagesFromIntent(intent)) {
                    app.sendMessageToServer(sms);
                }
                
                if (!app.getKeepInInbox())
                {
                    this.abortBroadcast();
                }
            }
        } catch (Throwable ex) {
            app.logError("Unexpected error in IncomingMessageForwarder", ex, true);
        }
    }

    // from http://github.com/dimagi/rapidandroid 
    // source: http://www.devx.com/wireless/Article/39495/1954
    private SmsMessage[] getMessagesFromIntent(Intent intent) {
        SmsMessage retMsgs[] = null;
        Bundle bdl = intent.getExtras();
        Object pdus[] = (Object[]) bdl.get("pdus");
        retMsgs = new SmsMessage[pdus.length];
        for (int n = 0; n < pdus.length; n++) {
            byte[] byteData = (byte[]) pdus[n];
            retMsgs[n] = SmsMessage.createFromPdu(byteData);
        }
        return retMsgs;
    }
}