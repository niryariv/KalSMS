package org.envaya.kalsms.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import java.util.ArrayList;
import java.util.List;
import org.envaya.kalsms.App;
import org.envaya.kalsms.IncomingMessage;
import org.envaya.kalsms.IncomingSms;


public class SmsReceiver extends BroadcastReceiver {

    private App app;   

    @Override
    // source: http://www.devx.com/wireless/Article/39495/1954
    public void onReceive(Context context, Intent intent) {        
        app = (App) context.getApplicationContext();
        
        if (!app.isEnabled())
        {
            return;
        }
        
        try {
            String action = intent.getAction();

            if (action.equals("android.provider.Telephony.SMS_RECEIVED")) {
                
                boolean hasUnhandledMessage = false;
                
                for (IncomingMessage sms : getMessagesFromIntent(intent)) {                    
                    
                    if (sms.isForwardable())
                    {                    
                        app.forwardToServer(sms);
                    }
                    else
                    {
                        hasUnhandledMessage = true;
                    }
                }
                
                if (!hasUnhandledMessage && !app.getKeepInInbox())
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
            messages.add(new IncomingSms(app, sms));
        }
        return messages;
    }
}