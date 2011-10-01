
package org.envaya.sms;

import android.net.Uri;
import android.telephony.SmsMessage;
import java.security.InvalidParameterException;
import java.util.List;
import org.apache.http.message.BasicNameValuePair;
import org.envaya.sms.task.ForwarderTask;


public class IncomingSms extends IncomingMessage {
    
    protected String message;
    
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
    
    // constructor for SMS retrieved from Messaging inbox
    public IncomingSms(App app, String from, String message, long timestampMillis) {
        super(app, from, timestampMillis);
        this.message = message;
    }        
    
    public String getMessageBody()
    {
        return message;
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

    public void tryForwardToServer() {        
        
        if (numRetries > 0)
        {
            app.log("Retrying forwarding SMS from " + from);
        }
        
        new ForwarderTask(this,
            new BasicNameValuePair("message_type", App.MESSAGE_TYPE_SMS),
            new BasicNameValuePair("message", getMessageBody())            
        ).execute();
    }
    
}
