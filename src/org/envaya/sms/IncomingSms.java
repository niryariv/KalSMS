
package org.envaya.sms;

import android.net.Uri;
import android.telephony.SmsMessage;
import java.security.InvalidParameterException;
import java.util.List;


public class IncomingSms extends IncomingMessage {
    
    // constructor for SMS retrieved from android.provider.Telephony.SMS_RECEIVED intent
    public IncomingSms(App app, List<SmsMessage> smsParts) throws InvalidParameterException {
        super(app, 
            smsParts.get(0).getOriginatingAddress(),
            smsParts.get(0).getTimestampMillis()
        );
        
        this.message = smsParts.get(0).getMessageBody();
        
        int numParts = smsParts.size();
        
        for (int i = 1; i < numParts; i++)
        {
            SmsMessage smsPart = smsParts.get(i);

            if (!smsPart.getOriginatingAddress().equals(from))
            {
                throw new InvalidParameterException(
                    "Tried to create IncomingSms from two different senders");
            }
            
            message = message + smsPart.getMessageBody();
        }
    }
        
    public IncomingSms(App app)
    {
        super(app);
    }
    
    public String getDisplayType()
    {
        return "SMS";
    }    
    
    public Uri getUri() 
    {
        return Uri.withAppendedPath(App.INCOMING_URI, 
                "sms/" + 
                Uri.encode(from) + "/" 
                + timestamp + "/" + 
                Uri.encode(message));
    }        
    
    public String getMessageType()
    {
        return App.MESSAGE_TYPE_SMS;
    }
}
