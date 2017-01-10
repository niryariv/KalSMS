package org.envaya.sms.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import java.util.ArrayList;
import java.util.List;
import org.envaya.sms.App;
import org.envaya.sms.IncomingMessage;
import org.envaya.sms.IncomingSms;

public class SmsReceiver extends BroadcastReceiver {

    private App app;   

    @Override
    public void onReceive(Context context, Intent intent) {        
        app = (App) context.getApplicationContext();
        
        if (!app.isEnabled())
        {
            return;
        }
        
        try {
            IncomingMessage sms = getMessageFromIntent(intent);
            
            if (sms.isForwardable())
            {                    
                app.inbox.forwardMessage(sms);

                if (!app.getKeepInInbox())
                {
                    this.abortBroadcast();
                }                    
            }
            else
            {
                app.log("Ignoring incoming SMS from " + sms.getFrom());
            }
        } catch (Throwable ex) {
            app.logError("Unexpected error in SmsReceiver", ex, true);
        }
    }

    private IncomingMessage getMessageFromIntent(Intent intent) 
    {
        Bundle bundle = intent.getExtras();        

        // SMSDispatcher may send us multiple pdus from a multipart sms,
        // in order (all in one broadcast though)
        
        // The comments in the gtalksms app indicate that we could get PDUs 
        // from multiple different senders at once, but I don't see how this
        // could happen by looking at the SMSDispatcher source code... 
        // so I'm going to assume it doesn't happen and throw an exception if
        // it does.        
        
        List<SmsMessage> smsParts = new ArrayList<SmsMessage>();
        
        for (Object pdu : (Object[]) bundle.get("pdus"))
        {
            smsParts.add(SmsMessage.createFromPdu((byte[]) pdu));
        }
                
        return new IncomingSms(app, smsParts);
    }
}