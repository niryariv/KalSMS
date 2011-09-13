package org.envaya.kalsms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import java.util.ArrayList;
import java.util.List;


public class IncomingMessageForwarder extends BroadcastReceiver {

    private App app;   

    @Override
    // source: http://www.devx.com/wireless/Article/39495/1954
    public void onReceive(Context context, Intent intent) {        
        app = App.getInstance(context.getApplicationContext());
        
        try {
            String action = intent.getAction();

            if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {
                
                for (IncomingMessage sms : getMessagesFromIntent(intent)) {                    
                    app.forwardToServer(sms);
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
    private List<IncomingMessage> getMessagesFromIntent(Intent intent) 
    {
        Bundle bundle = intent.getExtras();        
        List<IncomingMessage> messages = new ArrayList<IncomingMessage>();
        
        for (Object pdu : (Object[]) bundle.get("pdus"))
        {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            messages.add(new IncomingMessage(app, sms));
        }
        return messages;
    }
}